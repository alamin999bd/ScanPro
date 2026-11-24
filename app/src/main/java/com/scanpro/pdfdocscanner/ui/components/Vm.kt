package com.scanpro.pdfdocscanner.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scanpro.pdfdocscanner.AppContainer

/** Creates a ViewModel that receives the manual [AppContainer] (no DI framework needed). */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    container: AppContainer,
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM = viewModel(
    key = key,
    factory = viewModelFactory { initializer { create(container) } },
)
