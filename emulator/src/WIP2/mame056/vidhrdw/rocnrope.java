/***************************************************************************

  vidhrdw.c

  Functions to emulate the video hardware of the machine.

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP2.mame056.vidhrdw;
import static mame056.palette.*;
import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.libc.cstring.memset;

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

public class rocnrope
{
	
	
	
	static int flipscreen=0;
	
	
	
	/***************************************************************************
	
	  Convert the color PROMs into a more useable format.
	
	  Roc'n Rope has one 32x8 palette PROM and two 256x4 lookup table PROMs
	  (one for characters, one for sprites).
	  The palette PROM is connected to the RGB output this way:
	
	  bit 7 -- 220 ohm resistor  -- BLUE
	        -- 470 ohm resistor  -- BLUE
	        -- 220 ohm resistor  -- GREEN
	        -- 470 ohm resistor  -- GREEN
	        -- 1  kohm resistor  -- GREEN
	        -- 220 ohm resistor  -- RED
	        -- 470 ohm resistor  -- RED
	  bit 0 -- 1  kohm resistor  -- RED
	
	***************************************************************************/
        public static int TOTAL_COLORS(int gfxn){
            return Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity;
        };
        
	public static void COLOR(char[] colortable, int gfxn, int offs, int value){
            colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs] = (char) value;
        };
        
	public static VhConvertColorPromPtr rocnrope_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
		int _palPos = 0;
	
	
		for (i = 0;i < Machine.drv.total_colors;i++)
		{
			int bit0,bit1,bit2;
                        int r, g, b;
	
			/* red component */
			bit0 = (color_prom.read() >> 0) & 0x01;
			bit1 = (color_prom.read() >> 1) & 0x01;
			bit2 = (color_prom.read() >> 2) & 0x01;
                        r=(0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
			palette[_palPos++] = (char) r;
			/* green component */
			bit0 = (color_prom.read() >> 3) & 0x01;
			bit1 = (color_prom.read() >> 4) & 0x01;
			bit2 = (color_prom.read() >> 5) & 0x01;
                        g=(0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
			palette[_palPos++] = (char) g;
			/* blue component */
			bit0 = 0;
			bit1 = (color_prom.read() >> 6) & 0x01;
			bit2 = (color_prom.read() >> 7) & 0x01;
                        b=(0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
			palette[_palPos++] = (char) b;
	
			color_prom.inc();
                        
                        palette_set_color(i,r,g,b);
		}
	
		/* color_prom now points to the beginning of the lookup table */
	
		/* sprites */
		for (i = 0;i < TOTAL_COLORS(1);i++){
			COLOR(colortable,1,i,color_prom.read() & 0x0f);
                        color_prom.inc();
                }
	
		/* characters */
		for (i = 0;i < TOTAL_COLORS(0);i++){
			COLOR(colortable,0,i,color_prom.read() & 0x0f);
                        color_prom.inc();
                }
            }
        };
	
	
	public static WriteHandlerPtr rocnrope_flipscreen_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (flipscreen != (~data & 1))
		{
			flipscreen = ~data & 1;
			memset(dirtybuffer,1,videoram_size[0]);
		}
	} };
	
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr rocnrope_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
	
	
		/* for every character in the Video RAM, check if it has been modified */
		/* since last time and update it accordingly. */
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				int sx,sy,flipx,flipy;
	
	
				dirtybuffer[offs] = 0;
	
				sx = offs % 32;
				sy = offs / 32;
				flipx = colorram.read(offs) & 0x40;
				flipy = colorram.read(offs) & 0x20;
				if (flipscreen != 0)
				{
					sx = 31 - sx;
					sy = 31 - sy;
					flipx = (flipx!=0)? 0 : 1;
					flipy = (flipy!=0)? 0 : 1;
				}
	
				drawgfx(tmpbitmap,Machine.gfx[0],
						videoram.read(offs) + 2 * (colorram.read(offs) & 0x80),
						colorram.read(offs) & 0x0f,
						flipx,flipy,
						8*sx,8*sy,
						Machine.visible_area,TRANSPARENCY_NONE,0);
			}
		}
	
	
		/* copy the temporary bitmap to the screen */
		copybitmap(bitmap,tmpbitmap,0,0,0,0,Machine.visible_area,TRANSPARENCY_NONE,0);
	
	
		/* Draw the sprites. */
		for (offs = spriteram_size[0] - 2;offs >= 0;offs -= 2)
		{
			drawgfx(bitmap,Machine.gfx[1],
					spriteram.read(offs + 1),
					spriteram_2.read(offs) & 0x0f,
					spriteram_2.read(offs) & 0x40,~spriteram_2.read(offs) & 0x80,
					240-spriteram.read(offs),spriteram_2.read(offs + 1),
					Machine.visible_area,TRANSPARENCY_COLOR,0);
		}
	} };
}
