package com.victorious.launch.core.launcher

import com.victorious.launch.core.backup.WorkspaceItem

object WorkspacePages {
    const val DEFAULT_ROWS = 5

    fun pageCount(items: List<WorkspaceItem>): Int =
        (items.maxOfOrNull { it.page } ?: 0) + 1

    fun itemsOnPage(items: List<WorkspaceItem>, page: Int): List<WorkspaceItem> =
        items.filter { it.page == page }

    fun nextEmptySlot(
        items: List<WorkspaceItem>,
        columns: Int,
        rows: Int = DEFAULT_ROWS,
    ): Triple<Int, Int, Int> {
        val cols = columns.coerceIn(3, 6)
        val occupied =
            items
                .filter { it.kind != WorkspaceItem.KIND_WIDGET }
                .map { Triple(it.page, it.x, it.y) }
                .toSet()
        var page = 0
        while (page < 32) {
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    if (Triple(page, x, y) !in occupied) {
                        return Triple(page, x, y)
                    }
                }
            }
            page++
        }
        return Triple(pageCount(items), 0, 0)
    }

    fun pinApp(items: List<WorkspaceItem>, packageName: String, columns: Int): List<WorkspaceItem> {
        if (items.any { it.kind == WorkspaceItem.KIND_APP && it.packageName == packageName }) {
            return items
        }
        val (page, x, y) = nextEmptySlot(items, columns)
        return items + WorkspaceItem(packageName = packageName, x = x, y = y, page = page)
    }

    fun createFolder(
        items: List<WorkspaceItem>,
        firstPackage: String,
        secondPackage: String,
        folderId: String,
        title: String,
        columns: Int,
    ): List<WorkspaceItem> {
        val remaining =
            items.filterNot {
                it.kind == WorkspaceItem.KIND_APP &&
                    (it.packageName == firstPackage || it.packageName == secondPackage)
            }
        val (page, x, y) = nextEmptySlot(remaining, columns)
        return remaining +
            WorkspaceItem(
                packageName = "",
                x = x,
                y = y,
                kind = WorkspaceItem.KIND_FOLDER,
                page = page,
                folderId = folderId,
                title = title,
                children = listOf(firstPackage, secondPackage).distinct(),
            )
    }

    fun addToFolder(items: List<WorkspaceItem>, folderId: String, packageName: String): List<WorkspaceItem> {
        return items.map { item ->
            if (item.kind == WorkspaceItem.KIND_FOLDER && item.folderId == folderId) {
                if (packageName in item.children) item
                else item.copy(children = item.children + packageName)
            } else {
                item
            }
        }.filterNot { it.kind == WorkspaceItem.KIND_APP && it.packageName == packageName }
    }

    fun removeItem(items: List<WorkspaceItem>, itemId: String): List<WorkspaceItem> =
        items.filterNot { it.itemId() == itemId }
}
