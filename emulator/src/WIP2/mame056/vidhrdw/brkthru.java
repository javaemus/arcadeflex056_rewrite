/***************************************************************************

	breakthru:vidhrdw.c

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
import static mame056.palette.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.memoryH.*;

import static WIP2.mame056.vidhrdw.generic.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;

public class brkthru
{
	
	
	public static UBytePtr brkthru_scroll = new UBytePtr();
	public static UBytePtr brkthru_videoram = new UBytePtr();
	public static int[] brkthru_videoram_size = new int[1];
	static int bgscroll;
	static int bgbasecolor;
	static int flipscreen;
	
	
	
	/***************************************************************************
	
	  Convert the color PROMs into a more useable format.
	
	  Break Thru has one 256x8 and one 256x4 palette PROMs.
	  I don't know for sure how the palette PROMs are connected to the RGB
	  output, but it's probably the usual:
	
	  bit 7 -- 220 ohm resistor  -- GREEN
	        -- 470 ohm resistor  -- GREEN
	        -- 1  kohm resistor  -- GREEN
	        -- 2.2kohm resistor  -- GREEN
	        -- 220 ohm resistor  -- RED
	        -- 470 ohm resistor  -- RED
	        -- 1  kohm resistor  -- RED
	  bit 0 -- 2.2kohm resistor  -- RED
	
	  bit 3 -- 220 ohm resistor  -- BLUE
	        -- 470 ohm resistor  -- BLUE
	        -- 1  kohm resistor  -- BLUE
	  bit 0 -- 2.2kohm resistor  -- BLUE
	
	***************************************************************************/
	static int TOTAL_COLORS(int gfxn) {
        return Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity;
    }
    public static VhConvertColorPromPtr brkthru_vh_convert_color_prom = new VhConvertColorPromPtr() {
        public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
            int i;
		//#define TOTAL_COLORS(gfxn) (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity)
            //#define COLOR(gfxn,offs) (colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs])

            int p_inc = 0;
            for (i = 0; i < Machine.drv.total_colors; i++) {
                int bit0, bit1, bit2, bit3, r, g, b;

                bit0 = (color_prom.read(0) >> 0) & 0x01;
                bit1 = (color_prom.read(0) >> 1) & 0x01;
                bit2 = (color_prom.read(0) >> 2) & 0x01;
                bit3 = (color_prom.read(0) >> 3) & 0x01;
                r = ((0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3));
                //palette[p_inc++]=(char) r;
                bit0 = (color_prom.read(0) >> 4) & 0x01;
                bit1 = (color_prom.read(0) >> 5) & 0x01;
                bit2 = (color_prom.read(0) >> 6) & 0x01;
                bit3 = (color_prom.read(0) >> 7) & 0x01;
                g=( 0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3);
                //palette[p_inc++]=((char) g);
                bit0 = (color_prom.read(Machine.drv.total_colors) >> 0) & 0x01;
                bit1 = (color_prom.read(Machine.drv.total_colors) >> 1) & 0x01;
                bit2 = (color_prom.read(Machine.drv.total_colors) >> 2) & 0x01;
                bit3 = (color_prom.read(Machine.drv.total_colors) >> 3) & 0x01;
                b=(0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3);
                //palette[p_inc++]=((char) b);
                
                palette_set_color(i,r,g,b);

                color_prom.inc();
            }
        }
    };
		
	/***************************************************************************
	
	  Start the video hardware emulation.
	
	***************************************************************************/
	public static VhStartPtr brkthru_vh_start = new VhStartPtr() { public int handler() 
	{
		if ((dirtybuffer = new char[videoram_size[0]]) == null)
		{
			generic_vh_stop.handler();
			return 1;
		}
		memset(dirtybuffer,1,videoram_size[0]);
	
		/* the background area is twice as wide as the screen */
		if ((tmpbitmap = bitmap_alloc(2*Machine.drv.screen_width,Machine.drv.screen_height)) == null)
		{
			//free(dirtybuffer);
                        dirtybuffer = null;
			return 1;
		}
	
		return 0;
	} };
	
	
	
	/***************************************************************************
	
	  Stop the video hardware emulation.
	
	***************************************************************************/
	public static VhStopPtr brkthru_vh_stop = new VhStopPtr() { public void handler() 
	{
		bitmap_free(tmpbitmap);
		//free(dirtybuffer);
                dirtybuffer = null;
	} };
	
	
	
	public static WriteHandlerPtr brkthru_1800_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (offset == 0)	/* low 8 bits of scroll */
			bgscroll = (bgscroll & 0x100) | data;
		else if (offset == 1)
		{
			int bankaddress;
			UBytePtr RAM = memory_region(REGION_CPU1);
	
	
			/* bit 0-2 = ROM bank select */
			bankaddress = 0x10000 + (data & 0x07) * 0x2000;
			cpu_setbank(1,new UBytePtr(RAM, bankaddress));
	
			/* bit 3-5 = background tiles color code */
			if (((data & 0x38) >> 2) != bgbasecolor)
			{
				bgbasecolor = (data & 0x38) >> 2;
				memset(dirtybuffer,1,videoram_size[0]);
			}
	
			/* bit 6 = screen flip */
			if (flipscreen != (data & 0x40))
			{
				flipscreen = data & 0x40;
				memset(dirtybuffer,1,videoram_size[0]);
			}
	
			/* bit 7 = high bit of scroll */
			bgscroll = (bgscroll & 0xff) | ((data & 0x80) << 1);
		}
	} };
	
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr brkthru_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
	
	
		/* for every character in the Video RAM, check if it has been modified */
		/* since last time and update it accordingly. */
		for (offs = videoram_size[0] - 2;offs >= 0;offs -= 2)
		{
			if ((dirtybuffer[offs] !=0) || (dirtybuffer[offs+1] != 0))
			{
				int sx,sy,code;
	
	
				dirtybuffer[offs] = dirtybuffer[offs+1] = 0;
	
				sx = (offs/2) / 16;
				sy = (offs/2) % 16;
				if (flipscreen != 0)
				{
					sx = 31 - sx;
					sy = 15 - sy;
				}
	
				code = videoram.read(offs) + 256 * (videoram.read(offs+1) & 3);
				drawgfx(tmpbitmap,Machine.gfx[1 + (code >> 7)],
						code & 0x7f,
						bgbasecolor + ((videoram.read(offs+1) & 0x04) >> 2),
						flipscreen,flipscreen,
						16*sx,16*sy,
						null,TRANSPARENCY_NONE,0);
			}
		}
	
	//spriteram.offset=0;
		/* copy the background graphics */
		{
			int scroll;
	
	
			if (flipscreen != 0) scroll = 256 + bgscroll;
			else scroll = -bgscroll;
			copyscrollbitmap(bitmap,tmpbitmap,1,new int[]{scroll},0,new int[]{0},Machine.visible_area,TRANSPARENCY_NONE,0);
		}
	
	
		/* Draw the sprites. Note that it is important to draw them exactly in this */
		/* order, to have the correct priorities. */
		for (offs = 0;offs < spriteram_size[0]; offs += 4)
		{
			if ((spriteram.read(offs) & 0x01) != 0)	/* enable */
			{
				int sx,sy,code,color;
	
	
				/* the meaning of bit 3 of [offs] is unknown */
	
				sx = 240 - spriteram.read(offs+3);
				if (sx < -7) sx += 256;
				sy = 240 - spriteram.read(offs+2);
				code = spriteram.read(offs+1) + 128 * (spriteram.read(offs) & 0x06);
				color = (spriteram.read(offs) & 0xe0) >> 5;
				if (flipscreen != 0)
				{
					sx = 240 - sx;
					sy = 240 - sy;
				}
	
				if ((spriteram.read(offs) & 0x10) != 0)	/* double height */
				{
					drawgfx(bitmap,Machine.gfx[9],
							code & ~1,
							color,
							flipscreen,flipscreen,
							sx,(flipscreen!=0)? sy + 16 : sy - 16,
							Machine.visible_area,TRANSPARENCY_PEN,0);
					drawgfx(bitmap,Machine.gfx[9],
							code | 1,
							color,
							flipscreen,flipscreen,
							sx,sy,
							Machine.visible_area,TRANSPARENCY_PEN,0);
	
					/* redraw with wraparound */
					drawgfx(bitmap,Machine.gfx[9],
							code & ~1,
							color,
							flipscreen,flipscreen,
							sx,((flipscreen!=0)? sy + 16 : sy - 16) + 256,
							Machine.visible_area,TRANSPARENCY_PEN,0);
					drawgfx(bitmap,Machine.gfx[9],
							code | 1,
							color,
							flipscreen,flipscreen,
							sx,sy + 256,
							Machine.visible_area,TRANSPARENCY_PEN,0);
				}
				else
				{
					drawgfx(bitmap,Machine.gfx[9],
							code,
							color,
							flipscreen,flipscreen,
							sx,sy,
							Machine.visible_area,TRANSPARENCY_PEN,0);
	
					/* redraw with wraparound */
					drawgfx(bitmap,Machine.gfx[9],
							code,
							color,
							flipscreen,flipscreen,
							sx,sy + 256,
							Machine.visible_area,TRANSPARENCY_PEN,0);
				}
			}
		}
	
	
		/* draw the frontmost playfield. They are characters, but draw them as sprites */
		for (offs = brkthru_videoram_size[0] - 1;offs >= 0;offs--)
		{
			int sx,sy;
	
	
			sx = offs % 32;
			sy = offs / 32;
			if (flipscreen != 0)
			{
				sx = 31 - sx;
				sy = 31 - sy;
			}
	
			drawgfx(bitmap,Machine.gfx[0],
					brkthru_videoram.read(offs),
					0,
					flipscreen,flipscreen,
					8*sx,8*sy,
					Machine.visible_area,TRANSPARENCY_PEN,0);
		}
	} };
}
