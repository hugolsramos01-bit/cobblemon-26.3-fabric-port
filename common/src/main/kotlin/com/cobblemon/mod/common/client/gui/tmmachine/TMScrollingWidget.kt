/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.tmmachine

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.network.chat.Component

/** Native list adapter restricted to the TM UI, retaining its row/scroll contract. */
abstract class TMScrollingWidget<T: TMScrollingWidget.Slot<T>>(top:Int=0,val left:Int=0,width:Int=10,
    height:Int=10,val slotHeight:Int=10,val scrollBarWidth:Int=5):
    ObjectSelectionList<T>(Minecraft.getInstance(),width,height,top+height,slotHeight) {
    init { x=left; centerListVertically=false }
    @get:JvmName("getTmScrollAmount") @set:JvmName("setTmScrollAmount")
    var scrollAmount:Double
        get()=scrollAmount()
        set(value){setScrollAmount(value)}
    val maxScroll:Int get()=maxScrollAmount()
    val maxPosition:Int get()=contentHeight()
    val scrollbarPosition:Int get()=scrollBarX()
    override fun scrollBarX()=left+width-scrollBarWidth
    override fun contentHeight()=children().size*slotHeight
    open fun getEntry(index:Int):T=children()[index]
    override fun getRowLeft()=left
    override fun getRowRight()=left+width
    override fun getRowWidth()=width
    override fun getRowTop(index:Int)=y+2-scrollAmount.toInt()+index*slotHeight
    override fun getRowBottom(index:Int)=getRowTop(index)+slotHeight
    override fun extractListBackground(context:GuiGraphicsExtractor) {}
    override fun extractListSeparators(context:GuiGraphicsExtractor) {}
    override fun extractSelection(context:GuiGraphicsExtractor,entry:T,color:Int) {}
    override fun extractWidgetRenderState(context:GuiGraphicsExtractor,mouseX:Int,mouseY:Int,delta:Float) {
        focused=if(isMouseOver(mouseX.toDouble(),mouseY.toDouble()))getEntryAtPosition(mouseX.toDouble(),mouseY.toDouble()) else null
        context.enableScissor(x,y,right,bottom)
        try { renderListItems(context,mouseX,mouseY,delta) } finally { context.disableScissor() }
        if(maxScroll>0)renderScrollbar(context,mouseX,mouseY,delta)
    }
    open fun renderListItems(context:GuiGraphicsExtractor,mouseX:Int,mouseY:Int,delta:Float) {
        children().forEachIndexed{index,entry->
            val top=getRowTop(index)
            if(getRowBottom(index)>=y && top<=bottom)
                entry.render(context,index,top,rowLeft,rowWidth,slotHeight,mouseX,mouseY,focused===entry,delta)
        }
    }
    open fun renderScrollbar(context:GuiGraphicsExtractor,mouseX:Int,mouseY:Int,delta:Float) {
        val barHeight=height
        val size=(barHeight*barHeight/maxPosition.coerceAtLeast(1)).coerceIn(1,barHeight.coerceAtLeast(1))
        val top=y+(scrollAmount*(barHeight-size)/maxScroll.coerceAtLeast(1)).toInt()
        context.fill(scrollBarX(),y,scrollBarX()+scrollBarWidth,bottom,0xFF000000.toInt())
        context.fill(scrollBarX(),top,scrollBarX()+scrollBarWidth,top+size,0xFF808080.toInt())
    }
    abstract class Slot<T:Slot<T>>:ObjectSelectionList.Entry<T>() {
        abstract fun render(context:GuiGraphicsExtractor,index:Int,y:Int,x:Int,entryWidth:Int,entryHeight:Int,
            mouseX:Int,mouseY:Int,hovered:Boolean,tickDelta:Float)
        override fun extractContent(context:GuiGraphicsExtractor,mouseX:Int,mouseY:Int,hovered:Boolean,delta:Float)=
            render(context,-1,y,x,width,height,mouseX,mouseY,hovered,delta)
    }
}
