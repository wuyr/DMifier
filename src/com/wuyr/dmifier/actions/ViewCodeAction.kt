package com.wuyr.dmifier.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.task.ProjectTaskManager
import com.intellij.util.io.URLUtil
import com.wuyr.dmifier.core.DMifier
import com.wuyr.dmifier.utils.invoke
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import java.io.File
import java.io.FileInputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Paths

class ViewCodeAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project?.let { project ->
            e.getData(PlatformDataKeys.VIRTUAL_FILE)?.let { file ->
                PsiManager.getInstance(project).findFile(file)?.fileType?.name == "JAVA"
            }
        } ?: false
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.compileAndShow(e.getData(LangDataKeys.PSI_FILE)?.virtualFile ?: return)
    }

    private fun Project.compileAndShow(target: VirtualFile) {
        ProjectTaskManager.getInstance(this).compile(target).onSuccess { result ->
            if (!result.hasErrors()) {
                val file = PsiManager.getInstance(this).findFile(target) ?: return@onSuccess
                val packageName = file::class.invoke<String>(file, "getPackageName")!!
                this.findClassFromOutputDirectories(target, packageName)?.let { outputFile ->
                    Messages.showMessageDialog(outputFile.convertToDexMakerCode(), "DexMaker", null)
                }
            }
        }
    }

    private fun VirtualFile.convertToDexMakerCode() = StringWriter(2048).use { sw ->
        runCatching {
            ClassReader(FileInputStream(path)).accept(TraceClassVisitor(null, DMifier(), PrintWriter(sw)), 0)
        }.getOrElse {
            it.printStackTrace(PrintWriter(sw))
        }
        sw.toString()
    }

    private fun Project.findClassFromOutputDirectories(target: VirtualFile, packageName: String): VirtualFile? {
        ProjectRootManager.getInstance(this).fileIndex.getModuleForFile(target)?.let { module ->
            module.getOutputDirectories(module.getModuleScope(false).contains(target)).forEach { outputDir ->
                outputDir?.let { dir ->
                    VirtualFileManager.getInstance().getFileSystem(URLUtil.FILE_PROTOCOL).refreshAndFindFileByPath(
                            Paths.get(dir, packageName, "${target.nameWithoutExtension}.class").toString()
                    )?.also { outputFile ->
                        outputFile.refresh(false, false)
                        return outputFile
                    }
                }
            }
        }
        return null
    }

    private fun Module.getOutputDirectories(isRelease: Boolean) = ArrayList<String?>().also { result ->
        CompilerModuleExtension.getInstance(this)?.let { moduleExtension ->
            CompilerProjectExtension.getInstance(project)?.let { projectExtension ->
                (if (isRelease) moduleExtension.compilerOutputPath else moduleExtension.compilerOutputPathForTests)
                        ?.let { result.add(it.path) } ?: projectExtension.compilerOutput?.let { result.add(it.path) }
                OrderEnumerationHandler.EP_NAME.extensions.forEach { factory ->
                    if (factory.isApplicable(this)) {
                        ArrayList<String>().also { urls ->
                            @Suppress("OverrideOnly")
                            factory.createHandler(this).addCustomModuleRoots(
                                    OrderRootType.CLASSES, ModuleRootManager.getInstance(this), urls, isRelease, !isRelease
                            )
                        }.forEach { url ->
                            result.add(VirtualFileManager.extractPath(url).replace('/', File.separatorChar))
                        }
                    }
                }
            }
        }
    }
}