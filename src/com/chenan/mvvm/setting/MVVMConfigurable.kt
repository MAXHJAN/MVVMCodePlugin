package com.chenan.mvvm.setting

import com.chenan.mvvm.code.TemplateCode
import com.chenan.mvvm.ui.MVVMSettingUI
import com.chenan.mvvm.ui.WriteCodeDialog
import com.chenan.mvvm.util.PluginHelper
import com.chenan.mvvm.util.Utils
import com.chenan.mvvm.util.pathByProject
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.util.PackageChooserDialog
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.search.FilenameIndex
import com.intellij.util.xml.PsiPackageConverter
import java.util.regex.Pattern
import javax.rmi.CORBA.Util
import javax.swing.JCheckBox
import javax.swing.JComponent

class MVVMConfigurable(private val project: Project) : SearchableConfigurable {

    private val helper = PluginHelper.getInstance(project)
    private val setting = MVVMStateComponent.getInstance(project)
    private val ui = MVVMSettingUI()
    private var selectedRetrofitPath: String = ""


    override fun getId(): String {
        return "setting.MVVMConfigurable"
    }

    override fun getDisplayName(): String {
        return "代码（MVVM）生成设置"
    }

    override fun reset() {
        helper.activityList.forEach {
            ui.comboBoxActivity.addItem(it.nameWithoutExtension)
        }
        helper.viewModelList.forEach { ui.comboBoxViewModel.addItem(it.nameWithoutExtension) }
        helper.layoutList.forEach { ui.comboBoxLayout.addItem(it.nameWithoutExtension) }
        helper.activityList.indexOfFirst { it.nameWithoutExtension == setting.activity }.let {
            if (it >= 0) {
                ui.comboBoxActivity.selectedIndex = it
            } else {
                setting.activity = ui.comboBoxActivity.selectedItem.toString()
            }
        }
        helper.viewModelList.indexOfFirst { it.nameWithoutExtension == setting.viewModel }.let {
            if (it >= 0) {
                ui.comboBoxViewModel.selectedIndex = it
            } else {
                setting.viewModel = ui.comboBoxViewModel.selectedItem.toString()
            }
        }
        helper.layoutList.indexOfFirst { it.nameWithoutExtension == setting.layout }.let {
            if (it >= 0) {
                ui.comboBoxLayout.selectedIndex = it
            } else {
                setting.layout = ui.comboBoxLayout.selectedItem.toString()
            }
        }
        ui.checkBoxRetrofit.isSelected = setting.isOpen
        ui.jPanelBean.isVisible = setting.isOpen
        ui.jPanelRetrofit.isVisible = setting.isOpen
        if (setting.isOpen) {
            ui.textFieldBeanPath.text = setting.beanPath
            println("setting.retrofitPath:${setting.retrofitPath}")
            if (setting.retrofitPath.isNotEmpty())
                VirtualFileManager.getInstance().findFileByUrl(setting.retrofitPath)?.let { virtualFile ->
                    PsiManager.getInstance(project).findFile(virtualFile)?.let { psiFile ->
                        ui.textFieldRetrofitPath.text = psiFile.pathByProject
                        PsiDocumentManager.getInstance(project).getDocument(psiFile)?.let { document ->
                            val p = Pattern.compile("(interface\\s\\w*)")
                            p.matcher(document.text)?.let { matcher ->
                                while (matcher.find()) {
                                    ui.comboBoxInterface.addItem(matcher.group(1))
                                }
                            }
                            if (setting.retrofitInterface.isNotEmpty())
                                ui.comboBoxInterface.selectedItem = setting.retrofitInterface
                        }
                    }
                }
        }

    }

    @Throws(NullPointerException::class)
    override fun apply() {
        setting.activity = ui.comboBoxActivity.selectedItem.toString()
        setting.viewModel = ui.comboBoxViewModel.selectedItem.toString()
        setting.layout = ui.comboBoxLayout.selectedItem.toString()
        setting.isOpen = ui.checkBoxRetrofit.isSelected
        if (ui.checkBoxRetrofit.isSelected) {
            when {
                ui.textFieldBeanPath.text.isNullOrEmpty() -> {
                    Utils.showError("Bean类路径不能为空")
                    throw NullPointerException("Bean类路径不能为空")
                }
                selectedRetrofitPath.isNullOrEmpty() -> {
                    Utils.showError("Retrofit路径不能为空")
                    throw NullPointerException("Bean类路径不能为空")
                }
                ui.comboBoxInterface.selectedItem == null -> {
                    Utils.showError("Retrofit interface不能为空")
                    throw NullPointerException("Bean类路径不能为空")
                }
                else -> {
                    setting.beanPath = ui.textFieldBeanPath.text
                    setting.retrofitPath = selectedRetrofitPath
                    setting.retrofitInterface = ui.comboBoxInterface.selectedItem.toString()
                }
            }
        } else {
            setting.beanPath = ""
            setting.retrofitPath = ""
            setting.retrofitInterface = ""
        }
    }

    override fun isModified(): Boolean {
        return ui.comboBoxActivity.selectedItem != setting.activity || ui.comboBoxViewModel.selectedItem != setting.viewModel || ui.comboBoxLayout.selectedItem != setting.layout
                || ui.checkBoxRetrofit.isSelected != setting.isOpen || ui.textFieldBeanPath.text != setting.beanPath || selectedRetrofitPath != setting.retrofitPath
                || ui.comboBoxInterface.selectedItem != setting.retrofitInterface
    }

    override fun createComponent(): JComponent? {
        ui.checkBoxRetrofit.isSelected = false
        ui.jPanelBean.isVisible = false
        ui.jPanelRetrofit.isVisible = false
        ui.textFieldRetrofitPath.isEditable = false
        ui.textFieldBeanPath.isEditable = false
        setEvent()
        return ui.contentPanel
    }

    private fun setEvent() {
        ui.checkBoxRetrofit.addActionListener { event ->
            (event.source as? JCheckBox)?.also {
                ui.jPanelBean.isVisible = it.isSelected
                ui.jPanelRetrofit.isVisible = it.isSelected
            }
        }
        ui.btActivity.addActionListener {
            val name = ui.comboBoxActivity.selectedItem.toString()
            WriteCodeDialog().also {
                it.title = "编辑 Activity"
                it.setListener(object : WriteCodeDialog.OnClickListener {
                    override fun onOk(name: String, content: String) {
                        val txtName = if (name.endsWith(".txt")) name else "$name.txt"
                        Utils.createCode(Utils.getPluginPath(), TemplateCode.TYPE_ACTIVITY, txtName, content)?.let { file ->
                            file.nameWithoutExtension.let { item ->
                                ui.comboBoxActivity.addItem(item)
                                ui.comboBoxActivity.selectedItem = item
                            }
                        } ?: Utils.showError("创建 $txtName.txt 失败")
                    }

                    override fun onCancel() {

                    }
                })
            }.showDialog(name, Utils.getActivityCode(name))
        }
        ui.btAddActivity.addActionListener {
            WriteCodeDialog().also {
                it.title = "Activity Code"
                it.setListener(object : WriteCodeDialog.OnClickListener {
                    override fun onOk(name: String, content: String) {
                        val txtName = if (name.endsWith(".txt")) name else "$name.txt"
                        Utils.createCode(Utils.getPluginPath(), TemplateCode.TYPE_ACTIVITY, txtName, content)?.let { file ->
                            file.nameWithoutExtension.let { item ->
                                ui.comboBoxActivity.addItem(item)
                                ui.comboBoxActivity.selectedItem = item
                            }
                        } ?: Utils.showError("创建 $txtName.txt 失败")
                    }

                    override fun onCancel() {

                    }
                })
            }.showDialog()
        }
        ui.btDeleteActivity.addActionListener {
            JBPopupFactory.getInstance().createConfirmation("确认删除${ui.comboBoxActivity.selectedItem}吗？", "确定", "取消", {
                val item = ui.comboBoxActivity.selectedItem
                if (Utils.deleteActivityCode(item.toString())) {
                    ui.comboBoxActivity.removeItem(item)
                }
            }, 0).showInFocusCenter()
        }
        ui.btAddViewModel.addActionListener {
            WriteCodeDialog().also {
                it.title = "ViewModel Code"
                it.setListener(object : WriteCodeDialog.OnClickListener {
                    override fun onOk(name: String, content: String) {
                        val txtName = if (name.endsWith(".txt")) name else "$name.txt"
                        Utils.createCode(Utils.getPluginPath(), TemplateCode.TYPE_VIEW_MODEL, txtName, content)?.let { file ->
                            file.nameWithoutExtension.let { item ->
                                ui.comboBoxViewModel.addItem(item)
                                ui.comboBoxViewModel.selectedItem = item
                            }
                        } ?: Utils.showError("创建 $txtName.txt 失败")
                    }

                    override fun onCancel() {

                    }
                })
            }.showDialog()
        }
        ui.btAddLayout.addActionListener {
            WriteCodeDialog().also {
                it.title = "Layout Code"
                it.setListener(object : WriteCodeDialog.OnClickListener {
                    override fun onOk(name: String, content: String) {
                        val txtName = if (name.endsWith(".txt")) name else "$name.txt"
                        Utils.createCode(Utils.getPluginPath(), TemplateCode.TYPE_LAYOUT, txtName, content)?.let { file ->
                            file.nameWithoutExtension.let { item ->
                                ui.comboBoxLayout.addItem(item)
                                ui.comboBoxLayout.selectedItem = item
                            }
                        } ?: Utils.showError("创建 $txtName.txt 失败")
                    }

                    override fun onCancel() {

                    }
                })
            }.showDialog()
        }
        ui.btSelectBeanPath.addActionListener {
            val chooser = PackageChooserDialog("选择新增Bean类所在包", project)
            chooser.selectPackage(ui.textFieldBeanPath.text)
            if (chooser.showAndGet()) {
                val mPackage = chooser.selectedPackage
                if (mPackage != null) {
                    ui.textFieldBeanPath.text = mPackage.qualifiedName
                }
            }
        }
        ui.btSelectRetrofit.addActionListener {
            val chooser = TreeClassChooserFactory.getInstance(project).createFileChooser("选择Retrofit接口类所在包", null, null, {
                it.name.endsWith(".kt")
            }, true, false)
            chooser.showDialog()
            chooser.selectedFile?.let { psiFile ->
                println(psiFile.name)
                ui.textFieldRetrofitPath.text = psiFile.pathByProject
                selectedRetrofitPath = psiFile.virtualFile.url
                ui.comboBoxInterface.removeAll()
                PsiDocumentManager.getInstance(project).getDocument(psiFile)?.let { document ->
                    val p = Pattern.compile("(interface\\s\\w*)")
                    p.matcher(document.text)?.let { matcher ->
                        while (matcher.find()) {
                            ui.comboBoxInterface.addItem(matcher.group(1))
                        }
                    }
                }
            }
        }
    }

}