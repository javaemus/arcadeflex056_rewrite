/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP2.mame056.vidhrdw;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;
import static WIP2.mame056.cpuintrf.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.common.libc.cstring.memset;
import static WIP2.mame056.drivers.kingobox.kingofb_nmi_enable;

public class kingobox
{
	
	public static UBytePtr kingobox_videoram1 = new UBytePtr();
	public static UBytePtr kingobox_colorram1 = new UBytePtr();
	public static int[] kingobox_videoram1_size = new int[1];
	public static UBytePtr kingobox_scroll_y = new UBytePtr();
	
	
	static int palette_bank;
	
	
	
	/***************************************************************************
	
	  Convert the color PROMs into a more useable format.
	
	  King of Boxer has three 256x4 palette PROMs, connected to the RGB output
	  this way:
	
	  bit 3 -- 180 ohm resistor  -- RED/GREEN/BLUE
	        -- 360 ohm resistor  -- RED/GREEN/BLUE
	        -- 750 ohm resistor  -- RED/GREEN/BLUE
	  bit 0 -- 1.5kohm resistor  -- RED/GREEN/BLUE
	
	  The foreground color code directly goes to the RGB output, this way:
	
	  bit 5 --  51 ohm resistor  -- RED
	  bit 4 --  51 ohm resistor  -- GREEN
	  bit 3 --  51 ohm resistor  -- BLUE
	
	***************************************************************************/
        public static int TOTAL_COLORS(int gfxn){
             return Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity;
        }
        
	public static void COLOR(char[] colortable, int gfxn, int offs, int value){
            colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs] = (char) value;
        }
        
	public static VhConvertColorPromPtr kingobox_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
                int _palette = 0;
		
		for (i = 0;i < 256;i++)
		{
			int bit0,bit1,bit2,bit3;
	
	
			/* red component */
			bit0 = (color_prom.read(0) >> 0) & 0x01;
			bit1 = (color_prom.read(0) >> 1) & 0x01;
			bit2 = (color_prom.read(0) >> 2) & 0x01;
			bit3 = (color_prom.read(0) >> 3) & 0x01;
			palette[_palette++] = (char) (0x10 * bit0 + 0x21 * bit1 + 0x45 * bit2 + 0x89 * bit3);
			/* green component */
			bit0 = (color_prom.read(256) >> 0) & 0x01;
			bit1 = (color_prom.read(256) >> 1) & 0x01;
			bit2 = (color_prom.read(256) >> 2) & 0x01;
			bit3 = (color_prom.read(256) >> 3) & 0x01;
			palette[_palette++] = (char) (0x10 * bit0 + 0x21 * bit1 + 0x45 * bit2 + 0x89 * bit3);
			/* blue component */
			bit0 = (color_prom.read(2*256) >> 0) & 0x01;
			bit1 = (color_prom.read(2*256) >> 1) & 0x01;
			bit2 = (color_prom.read(2*256) >> 2) & 0x01;
			bit3 = (color_prom.read(2*256) >> 3) & 0x01;
			palette[_palette++] = (char) (0x10 * bit0 + 0x21 * bit1 + 0x45 * bit2 + 0x89 * bit3);
	
			color_prom.inc();
		}
	
	
		/* the foreground chars directly map to primary colors */
		for (i = 0;i < 8;i++)
		{
			/* red component */
			palette[_palette++] = (char) (0xff * ((i >> 2) & 0x01));
			/* green component */
			palette[_palette++] = (char) (0xff * ((i >> 1) & 0x01));
			/* blue component */
			palette[_palette++] = (char) (0xff * ((i >> 0) & 0x01));
		}
	
		for (i = 0;i < TOTAL_COLORS(0)/2;i++)
		{
			COLOR(colortable,0,2*i+0,0);	/* transparent */
			COLOR(colortable,0,2*i+1,256 + i);
		}
            }
        };
	
	/* Ring King has one 256x8 PROM instead of two 256x4 */
	public static VhConvertColorPromPtr ringking_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
		//#define TOTAL_COLORS(gfxn) (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity)
		//#define COLOR(gfxn,offs) (colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs])
                int _palette = 0;
	
		for (i = 0;i < 256;i++)
		{
			int bit0,bit1,bit2,bit3;
	
	
			/* red component */
			bit0 = (color_prom.read(0) >> 4) & 0x01;
			bit1 = (color_prom.read(0) >> 5) & 0x01;
			bit2 = (color_prom.read(0) >> 6) & 0x01;
			bit3 = (color_prom.read(0) >> 7) & 0x01;
			palette[_palette++] = (char) (0x10 * bit0 + 0x21 * bit1 + 0x45 * bit2 + 0x89 * bit3);
			/* green component */
			bit0 = (color_prom.read(0) >> 0) & 0x01;
			bit1 = (color_prom.read(0) >> 1) & 0x01;
			bit2 = (color_prom.read(0) >> 2) & 0x01;
			bit3 = (color_prom.read(0) >> 3) & 0x01;
			palette[_palette++] = (char) (0x10 * bit0 + 0x21 * bit1 + 0x45 * bit2 + 0x89 * bit3);
			/* blue component */
			bit0 = (color_prom.read(256) >> 0) & 0x01;
			bit1 = (color_prom.read(256) >> 1) & 0x01;
			bit2 = (color_prom.read(256) >> 2) & 0x01;
			bit3 = (color_prom.read(256) >> 3) & 0x01;
			palette[_palette++] = (char) (0x10 * bit0 + 0x21 * bit1 + 0x45 * bit2 + 0x89 * bit3);
	
			color_prom.inc();
		}
	
	
		/* the foreground chars directly map to primary colors */
		for (i = 0;i < 8;i++)
		{
			/* red component */
			palette[_palette++] = (char) (0xff * ((i >> 2) & 0x01));
			/* green component */
			palette[_palette++] = (char) (0xff * ((i >> 1) & 0x01));
			/* blue component */
			palette[_palette++] = (char) (0xff * ((i >> 0) & 0x01));
		}
	
		for (i = 0;i < TOTAL_COLORS(0)/2;i++)
		{
			COLOR(colortable,0,2*i+0,0);	/* transparent */
			COLOR(colortable,0,2*i+1,256 + i);
		}
            }
        };
	
	public static WriteHandlerPtr kingofb_f800_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		kingofb_nmi_enable = data & 0x20;
	
		if (palette_bank != ((data & 0x18) >> 3))
		{
			palette_bank = (data & 0x18) >> 3;
			memset(dirtybuffer,1,videoram_size[0]);
		}
	} };
	
	
	
	public static VhUpdatePtr kingobox_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
	
	
	
		/* background */
	
		/* for every character in the Video RAM, check if it has been modified */
		/* since last time and update it accordingly. */
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				int sx,sy,code,bank;
	
	
				sx = offs / 16;
				sy = 15 - offs % 16;
	
				dirtybuffer[offs] = 0;
	
				code = videoram.read(offs) + ((colorram.read(offs) & 0x03) << 8);
				bank = (colorram.read(offs) & 0x04) >> 2;
	
				drawgfx(tmpbitmap,Machine.gfx[2 + bank],
						code,
						((colorram.read(offs) & 0x70) >> 4) + 8 * palette_bank,
						0,0,
						sx*16,sy*16,
						null,TRANSPARENCY_NONE,0);
			}
		}
	
		{
			int scrolly = kingobox_scroll_y.read(0);
	
			copyscrollbitmap(bitmap,tmpbitmap,0,new int[]{0},1,new int[]{scrolly},Machine.visible_area,TRANSPARENCY_NONE,0);
		}
	
	
		/* sprites */
		for (offs = spriteram_size[0] - 4;offs >= 0;offs -= 4)
		{
			int sx,sy,code,color,bank,flipy;
	
	
			sx = spriteram.read(offs+1);
			sy = spriteram.read(offs);
	
			code = spriteram.read(offs + 2) + ((spriteram.read(offs + 3) & 0x03) << 8);
			bank = (spriteram.read(offs + 3) & 0x04) >> 2;
			color = ((spriteram.read(offs + 3) & 0x70) >> 4) + 8 * palette_bank;
			flipy = spriteram.read(offs + 3) & 0x80;
	
			drawgfx(bitmap,Machine.gfx[2 + bank],
						code,
						color,
						0,flipy,
						sx,sy,
						null,TRANSPARENCY_PEN,0);
		}
	
	
		/* foreground */
		for (offs = kingobox_videoram1_size[0] - 1;offs >= 0;offs--)
		{
			int sx,sy,code,bank;
	
	
			sx = offs / 32;
			sy = 31 - offs % 32;
	
			code = kingobox_videoram1.read(offs) + ((kingobox_colorram1.read(offs) & 0x01) << 8);
			bank = (kingobox_colorram1.read(offs) & 0x02) >> 1;
	
			drawgfx(bitmap,Machine.gfx[bank],
					code,
					(kingobox_colorram1.read(offs) & 0x38) >> 3,
					0,0,
					sx*8,sy*8,
					Machine.visible_area,TRANSPARENCY_PEN,0);
		}
	} };
	
	public static VhUpdatePtr ringking_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
	
	
	
		/* background */
	
		/* for every character in the Video RAM, check if it has been modified */
		/* since last time and update it accordingly. */
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				int sx,sy;
	
	
				sx = offs / 16;
				sy = 15 - offs % 16;
	
				dirtybuffer[offs] = 0;
	
				drawgfx(tmpbitmap,Machine.gfx[4],
						sx!=0 ? videoram.read(offs) : 0,	/* make the top row black */
						((colorram.read(offs) & 0x70) >> 4 ) + 8 * palette_bank,
						0,0,
						sx*16,sy*16,
						null,TRANSPARENCY_NONE,0);
			}
		}
	
		{
			int scrolly = kingobox_scroll_y.read(0);
	
			copyscrollbitmap(bitmap,tmpbitmap,0,new int[]{0},1,new int[]{scrolly},Machine.visible_area,TRANSPARENCY_NONE,0);
		}
	
	
		/* sprites */
		for (offs = 0; offs < spriteram_size[0];offs += 4)
		{
			int sx,sy,code,color,bank,flipy;
	
			sx = spriteram.read(offs+2);
			sy = spriteram.read(offs);
	
			code = spriteram.read(offs + 3) + ((spriteram.read(offs + 1) & 0x03) << 8);
			bank = (spriteram.read(offs + 1) & 0x04) >> 2;
			color = ((spriteram.read(offs + 1) & 0x70) >> 4) + 8 * palette_bank;
			flipy = ( spriteram.read(offs + 1) & 0x80 )!=0 ? 0 : 1;
	
			drawgfx(bitmap,Machine.gfx[2 + bank],
						code,
						color,
						0,flipy,
						sx,sy,
						null,TRANSPARENCY_PEN,0);
		}
	
	
		/* foreground */
		for (offs = kingobox_videoram1_size[0] - 1;offs >= 0;offs--)
		{
			int sx,sy,code,bank;
	
	
			sx = offs / 32;
			sy = 31 - offs % 32;
	
			code = kingobox_videoram1.read(offs) + ((kingobox_colorram1.read(offs) & 0x01) << 8);
			bank = (kingobox_colorram1.read(offs) & 0x02) >> 1;
	
			drawgfx(bitmap,Machine.gfx[bank],
					code,
					(kingobox_colorram1.read(offs) & 0x38) >> 3,
					0,0,
					sx*8,sy*8,
					Machine.visible_area,TRANSPARENCY_PEN,0);
		}
	} };
}
