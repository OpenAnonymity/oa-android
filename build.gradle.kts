import ai.openanonymity.android.build.OaChatBuildLayout
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}

abstract class PrepareOaChatDistTask : DefaultTask() {
    @get:InputDirectory
    abstract val oaChatDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun prepare() {
        val oaChatRoot = oaChatDir.get().asFile
        require(oaChatRoot.resolve("package.json").isFile) {
            "Missing oa-chat submodule at ${oaChatRoot.absolutePath}. Run `git submodule update --init --recursive`."
        }

        fun run(vararg command: String) {
            project.exec {
                workingDir = oaChatRoot
                commandLine(*command)
            }.assertNormalExitValue()
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
}

val generatedAssetsDir = layout.buildDirectory.dir(OaChatBuildLayout.generatedAssetsDistSubpath())

tasks.register<PrepareOaChatDistTask>("prepareOaChatDist") {
    group = "build"
    description = "Builds oa-chat and copies dist into generated Android assets."
    oaChatDir.set(layout.projectDirectory.dir(OaChatBuildLayout.OA_CHAT_SUBMODULE_DIR))
    outputDir.set(generatedAssetsDir)
}
