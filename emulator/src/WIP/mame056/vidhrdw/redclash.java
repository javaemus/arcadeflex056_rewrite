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

import static WIP2.common.ptr.*;
import static WIP2.common.libc.expressions.*;

import static WIP2.mame056.common.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.common.libc.cstring.memset;

import static WIP2.mame056.vidhrdw.generic.*;


public class redclash
{
	
	
	public static UBytePtr redclash_textram = new UBytePtr();
	
	static int star_speed;
	static int gfxbank;
	
	
	/***************************************************************************
	
	  Convert the color PROMs into a more useable format.
	
	  I'm using the same palette conversion as Lady Bug, but the Zero Hour
	  schematics show a different resistor network.
	
	***************************************************************************/
	public static VhConvertColorPromPtr redclash_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
	
	
		for (i = 0;i < 32;i++)
		{
			int bit1,bit2;
	
	
			bit1 = (color_prom.read(i) >> 0) & 0x01;
			bit2 = (color_prom.read(i) >> 5) & 0x01;
			palette[3*i] = (char) (0x47 * bit1 + 0x97 * bit2);
			bit1 = (color_prom.read(i) >> 2) & 0x01;
			bit2 = (color_prom.read(i) >> 6) & 0x01;
			palette[3*i + 1] = (char) (0x47 * bit1 + 0x97 * bit2);
			bit1 = (color_prom.read(i) >> 4) & 0x01;
			bit2 = (color_prom.read(i) >> 7) & 0x01;
			palette[3*i + 2] = (char) (0x47 * bit1 + 0x97 * bit2);
		}
	
		/* characters */
		for (i = 0;i < 8;i++)
		{
			colortable[4 * i] = 0;
			colortable[4 * i + 1] = (char) (i + 0x08);
			colortable[4 * i + 2] = (char) (i + 0x10);
			colortable[4 * i + 3] = (char) (i + 0x18);
		}
	
		/* sprites */
		for (i = 0;i < 4 * 8;i++)
		{
			int bit0,bit1,bit2,bit3;
	
	
			/* low 4 bits are for sprite n */
			bit0 = (color_prom.read(i + 32) >> 3) & 0x01;
			bit1 = (color_prom.read(i + 32) >> 2) & 0x01;
			bit2 = (color_prom.read(i + 32) >> 1) & 0x01;
			bit3 = (color_prom.read(i + 32) >> 0) & 0x01;
			colortable[i + 4 * 8] = (char) (1 * bit0 + 2 * bit1 + 4 * bit2 + 8 * bit3);
	
			/* high 4 bits are for sprite n + 8 */
			bit0 = (color_prom.read(i + 32) >> 7) & 0x01;
			bit1 = (color_prom.read(i + 32) >> 6) & 0x01;
			bit2 = (color_prom.read(i + 32) >> 5) & 0x01;
			bit3 = (color_prom.read(i + 32) >> 4) & 0x01;
			colortable[i + 4 * 16] = (char) (1 * bit0 + 2 * bit1 + 4 * bit2 + 8 * bit3);
		}
            }
        };
	
	public static WriteHandlerPtr redclash_gfxbank_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		gfxbank = data & 1;
	} };
	
	public static WriteHandlerPtr redclash_flipscreen_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		flip_screen_set(data & 1);
	} };
	
	/*
	star_speed:
	0 = unused
	1 = unused
	2 = forward fast
	3 = forward medium
	4 = forward slow
	5 = backwards slow
	6 = backwards medium
	7 = backwards fast
	*/
	public static WriteHandlerPtr redclash_star0_w = new WriteHandlerPtr() {public void handler(int offset, int data) { star_speed = (star_speed & ~1) | ((data & 1) << 0); } };
	public static WriteHandlerPtr redclash_star1_w = new WriteHandlerPtr() {public void handler(int offset, int data) { star_speed = (star_speed & ~2) | ((data & 1) << 1); } };
	public static WriteHandlerPtr redclash_star2_w = new WriteHandlerPtr() {public void handler(int offset, int data) { star_speed = (star_speed & ~4) | ((data & 1) << 2); } };
	public static WriteHandlerPtr redclash_star_reset_w = new WriteHandlerPtr() {public void handler(int offset, int data) { } };
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr redclash_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int i,offs;
	
	
		fillbitmap(bitmap,Machine.pens.read(0),Machine.visible_area);
	
		for (offs = spriteram_size[0] - 0x20;offs >= 0;offs -= 0x20)
		{
			i = 0;
			while (i < 0x20 && spriteram.read(offs + i) != 0)
				i += 4;
	
			while (i > 0)
			{
				i -= 4;
	
				if ((spriteram.read(offs + i) & 0x80) != 0)
				{
					int color = spriteram.read(offs + i + 2) & 0x0f;
					int sx = spriteram.read(offs + i + 3);
					int sy = offs / 4 + (spriteram.read(offs + i) & 0x07);
	
	
					switch ((spriteram.read(offs + i) & 0x18) >> 3)
					{
						case 3:	/* 24x24 */
						{
							int code = ((spriteram.read(offs + i + 1) & 0xf0) >> 4) + ((gfxbank & 1) << 4);
	
							drawgfx(bitmap,Machine.gfx[3],
									code,
									color,
									0,0,
									sx,sy - 16,
									Machine.visible_area,TRANSPARENCY_PEN,0);
							/* wraparound */
							drawgfx(bitmap,Machine.gfx[3],
									code,
									color,
									0,0,
									sx - 256,sy - 16,
									Machine.visible_area,TRANSPARENCY_PEN,0);
							break;
						}
	
						case 2:	/* 16x16 */
							if ((spriteram.read(offs + i) & 0x20) != 0)	/* zero hour spaceships */
							{
								int code = ((spriteram.read(offs + i + 1) & 0xf8) >> 3) + ((gfxbank & 1) << 5);
								int bank = (spriteram.read(offs + i + 1) & 0x02) >> 1;
	
								drawgfx(bitmap,Machine.gfx[4+bank],
										code,
										color,
										0,0,
										sx,sy - 16,
										Machine.visible_area,TRANSPARENCY_PEN,0);
							}
							else
							{
								int code = ((spriteram.read(offs + i + 1) & 0xf0) >> 4) + ((gfxbank & 1) << 4);
	
								drawgfx(bitmap,Machine.gfx[2],
										code,
										color,
										0,0,
										sx,sy - 16,
										Machine.visible_area,TRANSPARENCY_PEN,0);
							}
							break;
	
						case 1:	/* 8x8 */
							drawgfx(bitmap,Machine.gfx[1],
									spriteram.read(offs + i + 1),// + 4 * (spriteram[offs + i + 2] & 0x10),
									color,
									0,0,
									sx,sy - 16,
									Machine.visible_area,TRANSPARENCY_PEN,0);
							break;
	
						case 0:
	/*TODO*///usrintf_showmessage("unknown sprite size 0");
							break;
					}
				}
			}
		}
	
		/* bullets */
		for (offs = 0;offs < 0x20;offs++)
		{
			int sx,sy;
	
	
	//		sx = redclash_textram[offs];
			sx = 8*offs + (redclash_textram.read(offs) & 7);	/* ?? */
			sy = 0xff - redclash_textram.read(offs + 0x20);
	
			if (sx >= Machine.visible_area.min_x && sx <= Machine.visible_area.max_x &&
					sy >= Machine.visible_area.min_y && sy <= Machine.visible_area.max_y)
				plot_pixel.handler(bitmap,sx,sy,Machine.pens.read(0x0e));
		}
	
		for (offs = 0;offs < 0x400;offs++)
		{
			int sx,sy;
	
	
			sx = offs % 32;
			sy = offs / 32;
			if (flip_screen() != 0)
			{
				sx = 31 - sx;
				sy = 31 - sy;
			}
	
			drawgfx(bitmap,Machine.gfx[0],
					redclash_textram.read(offs),
					(redclash_textram.read(offs) & 0x70) >> 4,	/* ?? */
					flip_screen(),flip_screen(),
					8*sx,8*sy,
					Machine.visible_area,TRANSPARENCY_PEN,0);
		}
	
	//usrintf_showmessage("%d%d%d",star2,star1,star0);
	} };
}
