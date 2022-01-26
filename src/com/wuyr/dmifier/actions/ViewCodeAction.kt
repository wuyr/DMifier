package com.wuyr.dmifier.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.*
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.task.ProjectTaskManager
import com.intellij.util.io.URLUtil
import com.wuyr.dmifier.core.DMifier
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import java.io.File
import java.io.FileInputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Paths

class ViewCodeAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val file = e.getData(PlatformDataKeys.VIRTUAL_FILE)
        val project = e.project
        e.presentation.isEnabled = file != null && project != null
                && PsiManager.getInstance(project).findFile(file)?.fileType?.name == "JAVA"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(LangDataKeys.PSI_FILE) ?: return
        val virtualFile = psiFile.virtualFile ?: return
        val projectTaskManager = ProjectTaskManager.getInstance(project)

        projectTaskManager.compile(virtualFile).onSuccess { result ->
            if (!result.hasErrors()) {
                val file = PsiManager.getInstance(project).findFile(virtualFile) ?: return@onSuccess
                val packageName = file::class.java.getMethod("getPackageName").invoke(file) as String

                ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(virtualFile)?.let { module ->
                    val virtualFileManager = VirtualFileManager.getInstance().getFileSystem(URLUtil.FILE_PROTOCOL)
                    module.getOutputDirectories(module.getModuleScope(false).contains(virtualFile)).forEach { dir ->
                        virtualFileManager.refreshAndFindFileByPath(
                            Paths.get(dir, packageName, "${virtualFile.nameWithoutExtension}.class").toString()
                        )?.let { outputFile ->
                            outputFile.refresh(false, false)
                            Messages.showMessageDialog(
                                StringWriter(10240).use { sw ->
                                    ClassReader(FileInputStream(outputFile.path)).accept(
                                        TraceClassVisitor(null, DMifier(), PrintWriter(sw)), 0
                                    )
                                    sw.toString()
                                }, "DexMaker", null
                            )
                        }
                    }
                }
            }
        }
    }

    private fun Module.getOutputDirectories(isRelease: Boolean) = ArrayList<String?>().also { result ->
        CompilerModuleExtension.getInstance(this)?.let { moduleExtension ->
            CompilerProjectExtension.getInstance(project)?.let { projectExtension ->
                (if (isRelease) moduleExtension.compilerOutputPath else moduleExtension.compilerOutputPathForTests)
                    ?.let { result.add(it.path) } ?: projectExtension.compilerOutput?.let { result.add(it.path) }
                val moduleRootManager = ModuleRootManager.getInstance(this)
                OrderEnumerationHandler.EP_NAME.extensions.forEach { factory ->
                    if (factory.isApplicable(this)) {
                        ArrayList<String>().also { urls ->
                            @Suppress("OverrideOnly")
                            factory.createHandler(this).addCustomModuleRoots(
                                OrderRootType.CLASSES, moduleRootManager, urls, isRelease, !isRelease
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