/***************************************************************************

  vidhrdw.c

  Functions to emulate the video hardware of the machine.

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP2.mame056.vidhrdw;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.libc.cstring.memset;
import static mame056.palette.*;
import static WIP2.common.ptr.*;
import WIP2.common.subArrays.IntArray;
import static WIP2.mame056.common.*;

import static WIP2.mame056.palette.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.cpuexec.*;

import static WIP2.mame056.vidhrdw.generic.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP2.mame056.inptport.readinputport;

public class espial
{
	
	
	
	public static UBytePtr espial_attributeram = new UBytePtr();
	public static UBytePtr espial_column_scroll= new UBytePtr();
	
	
	
	/***************************************************************************
	
	  Convert the color PROMs into a more useable format.
	
	  Espial has two 256x4 palette PROMs.
	
	  I don't know for sure how the palette PROMs are connected to the RGB
	  output, but it's probably the usual:
	
	  bit 3 -- 220 ohm resistor  -- BLUE
	        -- 470 ohm resistor  -- BLUE
	        -- 220 ohm resistor  -- GREEN
	  bit 0 -- 470 ohm resistor  -- GREEN
	  bit 3 -- 1  kohm resistor  -- GREEN
	        -- 220 ohm resistor  -- RED
	        -- 470 ohm resistor  -- RED
	  bit 0 -- 1  kohm resistor  -- RED
	
	***************************************************************************/
	public static VhConvertColorPromPtr espial_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
	
	
		for (i = 0;i < Machine.drv.total_colors;i++)
		{
			int bit0,bit1,bit2,r,g,b;
	
	
			/* red component */
			bit0 = (color_prom.read(i) >> 0) & 0x01;
			bit1 = (color_prom.read(i) >> 1) & 0x01;
			bit2 = (color_prom.read(i) >> 2) & 0x01;
			r = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
			/* green component */
			bit0 = (color_prom.read(i) >> 3) & 0x01;
			bit1 = (color_prom.read(i + Machine.drv.total_colors) >> 0) & 0x01;
			bit2 = (color_prom.read(i + Machine.drv.total_colors) >> 1) & 0x01;
			g = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
			/* blue component */
			bit0 = 0;
			bit1 = (color_prom.read(i + Machine.drv.total_colors) >> 2) & 0x01;
			bit2 = (color_prom.read(i + Machine.drv.total_colors) >> 3) & 0x01;
			b = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
	
			palette_set_color(i,r,g,b);
		}
            }
        };
	
	public static WriteHandlerPtr espial_attributeram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (espial_attributeram.read(offset) != data)
		{
			espial_attributeram.write(offset, data);
			memset(dirtybuffer,1,videoram_size[0]);
		}
	} };
	
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr espial_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
	
	
		/* for every character in the Video RAM, check if it has been modified */
		/* since last time and update it accordingly. */
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				int sx,sy;
	
	
				dirtybuffer[offs] = 0;
	
				sx = offs % 32;
				sy = offs / 32;
	
				drawgfx(tmpbitmap,Machine.gfx[0],
						videoram.read(offs) + 256*(espial_attributeram.read(offs) & 0x03),
						colorram.read(offs),
						espial_attributeram.read(offs) & 0x04,espial_attributeram.read(offs) & 0x08,
						8*sx,8*sy,
						null,TRANSPARENCY_NONE,0);
			}
		}
	
	
		/* copy the temporary bitmap to the screen */
		{
			int[] scroll=new int[32];
	
	
			for (offs = 0;offs < 32;offs++)
				scroll[offs] = -espial_column_scroll.read(offs);
	
			copyscrollbitmap(bitmap,tmpbitmap,0,new int[]{0},32,scroll,Machine.visible_area,TRANSPARENCY_NONE,0);
		}
	
	
		/* Draw the sprites. Note that it is important to draw them exactly in this */
		/* order, to have the correct priorities. */
		for (offs = 0;offs < spriteram_size[0]/2;offs++)
		{
			int sx,sy,code,color,flipx,flipy;
	
	
			sx = spriteram.read(offs + 16);
			sy = 240 - spriteram_2.read(offs);
			code = spriteram.read(offs) >> 1;
			color = spriteram_2.read(offs + 16);
			flipx = spriteram_3.read(offs) & 0x04;
			flipy = spriteram_3.read(offs) & 0x08;
	
			if ((spriteram.read(offs) & 1) != 0)	/* double height */
			{
				drawgfx(bitmap,Machine.gfx[1],
						code,
						color,
						flipx,flipy,
						sx,sy - 16,
						Machine.visible_area,TRANSPARENCY_PEN,0);
				drawgfx(bitmap,Machine.gfx[1],
						code + 1,
						color,
						flipx,flipy,
						sx,sy,
						Machine.visible_area,TRANSPARENCY_PEN,0);
			}
			else
				drawgfx(bitmap,Machine.gfx[1],
						code,
						color,
						flipx,flipy,
						sx,sy,
						Machine.visible_area,TRANSPARENCY_PEN,0);
		}
	} };
}
