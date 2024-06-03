package tasks

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import javax.inject.Inject

open class UnpackageCodegenSources @Inject constructor(objects: ObjectFactory, layout: ProjectLayout) : Copy() {
    @get:InputFiles
    val codegenFiles: ConfigurableFileCollection = objects.fileCollection()

    @get:OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()
        .convention(layout.buildDirectory.dir("codegen"))

    init {
        from(codegenFiles)
        into(outputDir)
        exclude { it.name == "META-INF" }
    }
}