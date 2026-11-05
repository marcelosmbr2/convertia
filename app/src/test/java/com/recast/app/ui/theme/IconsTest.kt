package com.recast.app.ui.theme

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.addPathNodes
import org.junit.Assert.assertTrue
import org.junit.Test

class IconsTest {

    @Test
    fun `todos os paths dos icones sao validos`() {
        RecastIcons.all.forEachIndexed { index, icon ->
            icon.paths.forEach { d ->
                val nodes = addPathNodes(d)
                assertTrue("ícone $index: '$d'", nodes.isNotEmpty())
                assertTrue("ícone $index começa sem MoveTo: '$d'", nodes.first() is PathNode.MoveTo)
            }
        }
    }
}
