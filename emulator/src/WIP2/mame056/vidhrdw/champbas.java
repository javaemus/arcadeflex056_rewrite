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


public class champbas
{
	
	
	
	static int gfxbank;
	
	
	/***************************************************************************
	
	  Convert the color PROMs into a more useable format.
	
	  Pac Man has a 16 bytes palette PROM and a 128 bytes color lookup table PROM.
	
	  Pengo has a 32 bytes palette PROM and a 256 bytes color lookup table PROM
	  (actually that's 512 bytes, but the high address bit is grounded).
	
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
            return (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity);
        }
        
	public static void COLOR(char[] colortable, int gfxn, int offs, int value){
            colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs] = (char) value;
        }
        
	public static VhConvertColorPromPtr champbas_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
                int _palette = 0;
		
		for (i = 0;i < Machine.drv.total_colors;i++)
		{
			int bit0,bit1,bit2;
	
	
			/* red component */
			bit0 = (color_prom.read() >> 0) & 0x01;
			bit1 = (color_prom.read() >> 1) & 0x01;
			bit2 = (color_prom.read() >> 2) & 0x01;
			palette[_palette++] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
			/* green component */
			bit0 = (color_prom.read() >> 3) & 0x01;
			bit1 = (color_prom.read() >> 4) & 0x01;
			bit2 = (color_prom.read() >> 5) & 0x01;
			palette[_palette++] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
			/* blue component */
			bit0 = 0;
			bit1 = (color_prom.read() >> 6) & 0x01;
			bit2 = (color_prom.read() >> 7) & 0x01;
			palette[_palette++] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
	
			color_prom.inc();
		}
	
		/* color_prom now points to the beginning of the lookup table */
	
		/* TODO: there are 32 colors in the palette, but we are suing only 16 */
		/* the only difference between the two banks is color #14, grey instead of green */
	
		/* character lookup table */
		/* sprites use the same color lookup table as characters */
		for (i = 0;i < TOTAL_COLORS(0);i++){
			COLOR(colortable,0,i, ((color_prom.read()) & 0x0f));
                        color_prom.inc();
                }
            }
        };
	
	public static WriteHandlerPtr champbas_gfxbank_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (gfxbank != (data & 1))
		{
			gfxbank = data & 1;
			memset(dirtybuffer,1,videoram_size[0]);
		}
	} };
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr champbas_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
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
	
				sx = 8 * (offs % 32);
				sy = 8 * (offs / 32);
	
				drawgfx(tmpbitmap,Machine.gfx[0 + gfxbank],
						videoram.read(offs),
						(colorram.read(offs) & 0x1f) + 32,
						0,0,
						sx,sy,
						Machine.visible_area,TRANSPARENCY_NONE,0);
			}
		}
	
	
		/* copy the character mapped graphics */
		copybitmap(bitmap,tmpbitmap,0,0,0,0,Machine.visible_area,TRANSPARENCY_NONE,0);
	
	
		/* Draw the sprites. Note that it is important to draw them exactly in this */
		/* order, to have the correct priorities. */
		for (offs = spriteram_size[0] - 2;offs >= 0;offs -= 2)
		{
			drawgfx(bitmap,Machine.gfx[2 + gfxbank],
					spriteram.read(offs) >> 2,
					spriteram.read(offs + 1),
					spriteram.read(offs) & 1,spriteram.read(offs) & 2,
					((256+16 - spriteram_2.read(offs + 1)) & 0xff) - 16,spriteram_2.read(offs) - 16,
					Machine.visible_area,TRANSPARENCY_COLOR,0);
		}
	} };
}
