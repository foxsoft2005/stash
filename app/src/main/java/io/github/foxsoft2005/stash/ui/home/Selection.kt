package io.github.foxsoft2005.stash.ui.home

import io.github.foxsoft2005.stash.data.Item

/**
 * Batch mode on the home grid. Empty = not in batch mode.
 *
 * The selection is always read through [visibleIn]: only items currently on
 * screen (after search and the Lent-out filter) count as selected, so an action
 * can never touch something the user can't see.
 */
data class Selection(val ids: Set<Long> = emptySet()) {

    /** The selected items among [visible], in grid order. */
    fun visibleIn(visible: List<Item>): List<Item> = visible.filter { it.id in ids }

    /** Long press: starts batch mode with this item, or toggles it if already in batch mode. */
    fun longPress(id: Long, visible: List<Item>): Selection =
        if (visibleIn(visible).isEmpty()) Selection(setOf(id)) else toggle(id, visible)

    /** Tap in batch mode. Unselecting the last item leaves batch mode. */
    fun toggle(id: Long, visible: List<Item>): Selection {
        val current = visibleIn(visible).mapTo(mutableSetOf()) { it.id }
        return Selection(if (id in current) current - id else current + id)
    }

    /** Selects everything on screen (respects search and filters). */
    fun selectAll(visible: List<Item>): Selection = Selection(visible.mapTo(mutableSetOf()) { it.id })
}

/** Items deleted with "Delete selected", hidden until the Undo window closes. */
data class PendingDeletion(val token: Long, val items: List<Item>) {
    val ids: Set<Long> = items.mapTo(mutableSetOf()) { it.id }
}
