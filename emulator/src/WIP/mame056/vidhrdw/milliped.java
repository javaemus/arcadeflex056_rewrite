/***************************************************************************

  vidhrdw.c

  Functions to emulate the video hardware of the machine.

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.vidhrdw;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.libc.cstring.memset;
import static mame056.palette.*;
import static WIP2.common.ptr.*;
import WIP2.common.subArrays.IntArray;
import static WIP2.mame056.common.*;

import static WIP2.mame056.palette.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;

import static WIP2.mame056.vidhrdw.generic.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP2.mame056.artworkH.*;
import static WIP2.mame056.artwork.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.timerH.*;
import static WIP2.mame056.timer.*;

import static WIP2.mame056.usrintrfH.UI_COLOR_NORMAL;


public class milliped
{
	
	
	
	static rectangle spritevisiblearea = new rectangle
	(
		1*8, 31*8-1,
		0*8, 30*8-1
	);
	
	
	
	/***************************************************************************
	
	  Millipede doesn't have a color PROM, it uses RAM.
	  The RAM seems to be conncted to the video output this way:
	
	  bit 7 red
	        red
	        red
	        green
	        green
	        blue
	        blue
	  bit 0 blue
	
	***************************************************************************/
	public static int TOTAL_COLORS(int gfxn){
            return (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity);
        }
        
	public static void COLOR(char []colortable, int gfxn, int offs, int value){
            (colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs])=(char) value;
        }
        
	public static VhConvertColorPromPtr milliped_init_palette = new VhConvertColorPromPtr() { public void handler(char []palette, char []colortable, UBytePtr color_prom) 
	{
		int i;
		int _palette = 0;
                
		/* characters use colors 0-15 */
		for (i = 0;i < TOTAL_COLORS(0);i++)
			COLOR(colortable,0,i,i);
	
		/* Millipede is unusual because the sprite color code specifies the */
		/* colors to use one by one, instead of a combination code. */
		/* bit 7-6 = palette bank (there are 4 groups of 4 colors) */
		/* bit 5-4 = color to use for pen 11 */
		/* bit 3-2 = color to use for pen 10 */
		/* bit 1-0 = color to use for pen 01 */
		/* pen 00 is transparent */
		for (i = 0;i < TOTAL_COLORS(1);i+=4)
		{
			COLOR(colortable,1,i+0, 16 + 4*((i >> 8) & 3));
			COLOR(colortable,1,i+1, 16 + 4*((i >> 8) & 3) + ((i >> 2) & 3));
			COLOR(colortable,1,i+2, 16 + 4*((i >> 8) & 3) + ((i >> 4) & 3));
			COLOR(colortable,1,i+3, 16 + 4*((i >> 8) & 3) + ((i >> 6) & 3));
		}
	} };
	
	
	public static WriteHandlerPtr milliped_paletteram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		int bit0,bit1,bit2;
		int r,g,b;
	
	
		paletteram.write(offset,data);
	
		/* red component */
		bit0 = (~data >> 5) & 0x01;
		bit1 = (~data >> 6) & 0x01;
		bit2 = (~data >> 7) & 0x01;
		r = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
	
		/* green component */
		bit0 = 0;
		bit1 = (~data >> 3) & 0x01;
		bit2 = (~data >> 4) & 0x01;
		g = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
	
		/* blue component */
		bit0 = (~data >> 0) & 0x01;
		bit1 = (~data >> 1) & 0x01;
		bit2 = (~data >> 2) & 0x01;
		b = 0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2;
	
		palette_set_color(offset,r,g,b);
	} };
	
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr milliped_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
	
	
		if (full_refresh != 0)
			memset (dirtybuffer, 1, videoram_size[0]);
	
		/* for every character in the Video RAM, check if it has been modified */
		/* since last time and update it accordingly. */
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				int sx,sy;
				int bank;
				int color;
	
	
				dirtybuffer[offs] = 0;
	
				sx = offs % 32;
				sy = offs / 32;
	
				if ((videoram.read(offs)& 0x40) != 0)
					bank = 1;
				else bank = 0;
	
				if ((videoram.read(offs)& 0x80) != 0)
					color = 2;
				else color = 0;
	
				drawgfx(bitmap,Machine.gfx[0],
						0x40 + (videoram.read(offs)& 0x3f) + 0x80 * bank,
						bank + color,
						0,0,
						8*sx,8*sy,
						Machine.visible_area,TRANSPARENCY_NONE,0);
			}
		}
	
	
		/* Draw the sprites */
		for (offs = 0;offs < 0x10;offs++)
		{
			int spritenum,color;
			int x, y;
			int sx, sy;
	
	
			x = spriteram.read(offs + 0x20);
			y = 240 - spriteram.read(offs + 0x10);
	
			spritenum = spriteram.read(offs)& 0x3f;
			if ((spritenum & 1) != 0) spritenum = spritenum / 2 + 64;
			else spritenum = spritenum / 2;
			color = spriteram.read(offs + 0x30);
	
			drawgfx(bitmap,Machine.gfx[1],
					spritenum,
					color,
					0,spriteram.read(offs)& 0x80,
					x,y,
					spritevisiblearea,TRANSPARENCY_PEN,0);
	
			/* mark tiles underneath as dirty */
			sx = x >> 3;
			sy = y >> 3;
	
			{
				int max_x = 1;
				int max_y = 2;
				int x2, y2;
	
				if ((x & 0x07) != 0) max_x ++;
				if ((y & 0x0f) != 0) max_y ++;
	
				for (y2 = sy; y2 < sy + max_y; y2 ++)
				{
					for (x2 = sx; x2 < sx + max_x; x2 ++)
					{
						if ((x2 < 32) && (y2 < 32) && (x2 >= 0) && (y2 >= 0))
							dirtybuffer[x2 + 32*y2] = 1;
					}
				}
			}
	
		}
	} };
}
