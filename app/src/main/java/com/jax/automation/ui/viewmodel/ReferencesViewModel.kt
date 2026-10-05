package com.jax.automation.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jax.automation.di.AppContainer
import com.jax.automation.models.ReferenceCategory
import com.jax.automation.models.ReferenceItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReferencesViewModel(private val container: AppContainer) : ViewModel() {

    private val _category = MutableStateFlow(ReferenceCategory.CHARACTERS)
    val category: StateFlow<ReferenceCategory> = _category

    val items: StateFlow<List<ReferenceItem>> = _category
        .flatMapLatest { container.references.observeByCategory(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setCategory(category: ReferenceCategory) {
        _category.value = category
    }

    fun upsert(item: ReferenceItem) = viewModelScope.launch {
        container.references.upsert(item)
    }

    fun delete(id: String) = viewModelScope.launch {
        container.references.delete(id)
    }
}
