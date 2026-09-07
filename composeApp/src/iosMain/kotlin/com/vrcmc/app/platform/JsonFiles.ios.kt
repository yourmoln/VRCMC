package com.vrcmc.app

import androidx.compose.runtime.*
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberJsonFileActions(
    onImport: (String) -> Unit,
    onExport: () -> Unit,
    onError: () -> Unit,
): JsonFileActions? {
    val importCallback by rememberUpdatedState(onImport)
    val exportCallback by rememberUpdatedState(onExport)
    val errorCallback by rememberUpdatedState(onError)
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            var exporting = false

            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                if (exporting) {
                    exportCallback()
                    return
                }
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                val access = url.startAccessingSecurityScopedResource()
                try {
                    val value = NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null)
                    if (value == null) errorCallback() else importCallback(value)
                } finally {
                    if (access) url.stopAccessingSecurityScopedResource()
                }
            }
        }
    }
    fun present(picker: UIDocumentPickerViewController) {
        var presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
        if (presenter == null) { errorCallback(); return }
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }
    return JsonFileActions(
        importFile = {
            delegate.exporting = false
            present(UIDocumentPickerViewController(documentTypes = listOf("public.json", "public.plain-text"),
                inMode = UIDocumentPickerMode.UIDocumentPickerModeImport))
        },
        exportFile = { value ->
            val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + "hotword-dictionary.json")
            if (NSString.create(string = value).writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)) {
                delegate.exporting = true
                present(UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true))
            } else errorCallback()
        },
    )
}
