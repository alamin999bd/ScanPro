package com.scanpro.pdfdocscanner.ui.navigation

object Routes {
    const val HOME = "home"
    const val SCAN = "scan/{mode}"
    const val IMAGE_TO_PDF = "image_to_pdf"
    const val PDF_TOOLS = "pdf_tools"
    const val IMPORT = "import"
    const val DOCUMENTS = "documents?search={search}"
    const val FOLDERS = "folders"
    const val FOLDER_DETAIL = "folder/{id}"
    const val VIEWER = "viewer/{id}"
    const val SETTINGS = "settings"

    const val ARG_MODE = "mode"
    const val ARG_ID = "id"
    const val ARG_SEARCH = "search"

    const val MODE_DOCUMENT = "document"
    const val MODE_ID = "id"

    fun scan(mode: String) = "scan/$mode"
    fun folder(id: Long) = "folder/$id"
    fun viewer(id: Long) = "viewer/$id"
    fun documents(focusSearch: Boolean = false) = "documents?search=$focusSearch"
}
