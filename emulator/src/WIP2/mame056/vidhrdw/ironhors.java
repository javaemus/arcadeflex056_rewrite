/***************************************************************************

  vidhrdw.c

  Functions to emulate the video hardware of the machine.

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP2.mame056.vidhrdw;

import static WIP2.mame056.usrintrf.*;
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


public class ironhors
{
	
	
	
	public static UBytePtr ironhors_scroll=new UBytePtr();
	static int palettebank,charbank,spriterambank;
	
	
	
	/***************************************************************************
	
	  Convert the color PROMs into a more useable format.
	
	  Iron Horse has three 256x4 palette PROMs (one per gun) and two 256x4
	  lookup table PROMs (one for characters, one for sprites).
	  I don't know for sure how the palette PROMs are connected to the RGB
	  output, but it's probably the usual:
	
	  bit 3 -- 220 ohm resistor  -- RED/GREEN/BLUE
	        -- 470 ohm resistor  -- RED/GREEN/BLUE
	        -- 1  kohm resistor  -- RED/GREEN/BLUE
	  bit 0 -- 2.2kohm resistor  -- RED/GREEN/BLUE
	
	***************************************************************************/
        public static int TOTAL_COLORS(int gfxn){
            return (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity);
        }
	
        public static void COLOR(char[] colortable, int gfxn, int offs, int value){
            (colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs]) = (char) value;
        }
        
	public static VhConvertColorPromPtr ironhors_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
                int _palette = 0;
		
		for (i = 0;i < Machine.drv.total_colors;i++)
		{
			int bit0,bit1,bit2,bit3;
	
	
			bit0 = (color_prom.read(0) >> 0) & 0x01;
			bit1 = (color_prom.read(0) >> 1) & 0x01;
			bit2 = (color_prom.read(0) >> 2) & 0x01;
			bit3 = (color_prom.read(0) >> 3) & 0x01;
			palette[_palette++] = (char) (0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3);
			bit0 = (color_prom.read(Machine.drv.total_colors) >> 0) & 0x01;
			bit1 = (color_prom.read(Machine.drv.total_colors) >> 1) & 0x01;
			bit2 = (color_prom.read(Machine.drv.total_colors) >> 2) & 0x01;
			bit3 = (color_prom.read(Machine.drv.total_colors) >> 3) & 0x01;
			palette[_palette++] = (char) (0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3);
			bit0 = (color_prom.read(2*Machine.drv.total_colors) >> 0) & 0x01;
			bit1 = (color_prom.read(2*Machine.drv.total_colors) >> 1) & 0x01;
			bit2 = (color_prom.read(2*Machine.drv.total_colors) >> 2) & 0x01;
			bit3 = (color_prom.read(2*Machine.drv.total_colors) >> 3) & 0x01;
			palette[_palette++] = (char) (0x0e * bit0 + 0x1f * bit1 + 0x43 * bit2 + 0x8f * bit3);
	
			color_prom.inc();
		}
	
		color_prom.inc( 2*Machine.drv.total_colors);
		/* color_prom now points to the beginning of the character lookup table */
	
	
		/* there are eight 32 colors palette banks; sprites use colors 0-15 and */
		/* characters 16-31 of each bank. */
		for (i = 0;i < TOTAL_COLORS(0)/8;i++)
		{
			int j;
	
	
			for (j = 0;j < 8;j++)
				COLOR(colortable,0,i + j * TOTAL_COLORS(0)/8, (color_prom.read() & 0x0f) + 32 * j + 16);
	
			color_prom.inc();
		}
	
		for (i = 0;i < TOTAL_COLORS(1)/8;i++)
		{
			int j;
	
	
			for (j = 0;j < 8;j++)
				COLOR(colortable,1,i + j * TOTAL_COLORS(1)/8, (color_prom.read() & 0x0f) + 32 * j);
	
			color_prom.inc();
		}
                
            }
            
        };
	
	public static WriteHandlerPtr ironhors_charbank_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (charbank != (data & 3))
		{
			charbank = data & 3;
			memset(dirtybuffer,1,videoram_size[0]);
		}
	
		spriterambank = data & 8;
	
		/* other bits unknown */
	} };
	
	
	
	public static WriteHandlerPtr ironhors_palettebank_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (palettebank != (data & 7))
		{
			palettebank = data & 7;
			memset(dirtybuffer,1,videoram_size[0]);
		}
	
		coin_counter_w.handler(0,data & 0x10);
		coin_counter_w.handler(1,data & 0x20);
	
	if ((data & 0xc8)!=0) usrintf_showmessage("ironhors_palettebank_w %02x",data);
	} };
	
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr ironhors_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs,i;
	
	
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
	
				drawgfx(tmpbitmap,Machine.gfx[0],
						videoram.read(offs) + ((colorram.read(offs) & 0x40)<<2) + ((colorram.read(offs) & 0x20)<<4) + (charbank<<10),
						(colorram.read(offs) & 0x0f) + 16 * palettebank,
						colorram.read(offs) & 0x10,colorram.read(offs) & 0x20,
						sx,sy,
						null,TRANSPARENCY_NONE,0);
			}
		}
	
	
		/* copy the temporary bitmap to the screen */
		{
			int[] scroll=new int[32];
	
	
			for (i = 0;i < 32;i++)
				scroll[i] = -(ironhors_scroll.read(i));
	
			copyscrollbitmap(bitmap,tmpbitmap,32,scroll,0,new int[]{0},Machine.visible_area,TRANSPARENCY_NONE,0);
		}
	
	
		/* Draw the sprites. */
		{
			UBytePtr sr=new UBytePtr();
	
	
			if (spriterambank != 0)
				sr = spriteram;
			else sr = spriteram_2;
	
			for (offs = 0;offs < spriteram_size[0];offs += 5)
			{
				int sx,sy,flipx,flipy,code,color;
	
	
				sx = sr.read(offs+3);
				sy = sr.read(offs+2);
				flipx = sr.read(offs+4) & 0x20;
				flipy = sr.read(offs+4) & 0x40;
				code = (sr.read(offs) << 2) + ((sr.read(offs+1) & 0x03) << 10) + ((sr.read(offs+1) & 0x0c) >> 2);
				color = ((sr.read(offs+1) & 0xf0)>>4) + 16 * palettebank;
	
				switch (sr.read(offs+4) & 0x0c)
				{
					case 0x00:	/* 16x16 */
						drawgfx(bitmap,Machine.gfx[1],
								code/4,
								color,
								flipx,flipy,
								sx,sy,
								Machine.visible_area,TRANSPARENCY_PEN,0);
						break;
	
					case 0x04:	/* 16x8 */
						{
							drawgfx(bitmap,Machine.gfx[2],
									code & ~1,
									color,
									flipx,flipy,
									flipx!=0?sx+8:sx,sy,
									Machine.visible_area,TRANSPARENCY_PEN,0);
							drawgfx(bitmap,Machine.gfx[2],
									code | 1,
									color,
									flipx,flipy,
									flipx!=0?sx:sx+8,sy,
									Machine.visible_area,TRANSPARENCY_PEN,0);
						}
						break;
	
					case 0x08:	/* 8x16 */
						{
							drawgfx(bitmap,Machine.gfx[2],
									code & ~2,
									color,
									flipx,flipy,
									sx,flipy!=0?sy+8:sy,
									Machine.visible_area,TRANSPARENCY_PEN,0);
							drawgfx(bitmap,Machine.gfx[2],
									code | 2,
									color,
									flipx,flipy,
									sx,flipy!=0?sy:sy+8,
									Machine.visible_area,TRANSPARENCY_PEN,0);
						}
						break;
	
					case 0x0c:	/* 8x8 */
						{
							drawgfx(bitmap,Machine.gfx[2],
									code,
									color,
									flipx,flipy,
									sx,sy,
									Machine.visible_area,TRANSPARENCY_PEN,0);
						}
						break;
				}
			}
		}
	} };
}
