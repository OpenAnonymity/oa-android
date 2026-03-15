import ai.openanonymity.android.build.OaChatBuildLayout
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}

abstract class PrepareOaChatDistTask : DefaultTask() {
    @get:Internal
    abstract val oaChatSubmoduleDir: DirectoryProperty

    @get:Internal
    abstract val workspaceOaChatDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun prepare() {
        val oaChatRoot = resolveOaChatRoot()
        require(oaChatRoot != null) {
            val submodulePath = oaChatSubmoduleDir.get().asFile.absolutePath
            val workspacePath = workspaceOaChatDir.get().asFile.absolutePath
            "Missing oa-chat checkout. Expected either $submodulePath or sibling workspace repo at $workspacePath."
        }
        require(oaChatRoot.resolve("package.json").isFile) {
            "Found oa-chat directory at ${oaChatRoot.absolutePath}, but package.json is missing."
        }

        fun run(vararg command: String) {
            project.providers.exec {
                workingDir = oaChatRoot
                commandLine(*command)
            }.result.get().assertNormalExitValue()
        }

        logger.lifecycle("[oa-chat] Installing dependencies...")
        run(OaChatBuildLayout.npmExecutable(), "install")

        logger.lifecycle("[oa-chat] Building dist...")
        run(OaChatBuildLayout.npmExecutable(), "run", "build")

        val distDir = oaChatRoot.resolve(OaChatBuildLayout.DIST_DIR_NAME)
        require(distDir.resolve("index.html").isFile) {
            "oa-chat build completed without dist/index.html at ${distDir.absolutePath}."
        }

        project.delete(outputDir.get().asFile)
        project.copy {
            from(distDir)
            into(outputDir.get().asFile)
        }
    }

    private fun resolveOaChatRoot(): java.io.File? {
        val submoduleDir = oaChatSubmoduleDir.orNull?.asFile
        val workspaceDir = workspaceOaChatDir.orNull?.asFile

        val submoduleIsInitialized = submoduleDir?.resolve(".git")?.exists() == true
        if (submoduleIsInitialized && submoduleDir?.resolve("package.json")?.isFile == true) {
            return submoduleDir
        }

        if (workspaceDir?.resolve("package.json")?.isFile == true) {
            return workspaceDir
        }

        return submoduleDir?.takeIf { it.resolve("package.json").isFile }
    }
}

val generatedAssetsDir = layout.buildDirectory.dir(OaChatBuildLayout.generatedAssetsDistSubpath())

tasks.register<PrepareOaChatDistTask>("prepareOaChatDist") {
    group = "build"
    description = "Builds oa-chat and copies dist into generated Android assets."
    oaChatSubmoduleDir.set(layout.projectDirectory.dir(OaChatBuildLayout.OA_CHAT_SUBMODULE_DIR))
    workspaceOaChatDir.set(layout.projectDirectory.dir("../oa-chat"))
    outputDir.set(generatedAssetsDir)
}
