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

public class wiping
{
	
	
	static int flipscreen;
	
	
	/***************************************************************************
	
	  Convert the color PROMs into a more useable format.
	
	***************************************************************************/
        public static int TOTAL_COLORS(int gfxn){
            return (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity);
        }
        
	public static void COLOR(char[] colortable, int gfxn, int offs, int value){
            (colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + (offs)])=(char) value;
        }
	
	public static VhConvertColorPromPtr wiping_vh_convert_color_prom = new VhConvertColorPromPtr() {
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
			bit0 = (color_prom.read() >> 6) & 0x01;
			bit1 = (color_prom.read() >> 7) & 0x01;
			palette[_palette++] = (char) (0x4f * bit0 + 0xa8 * bit1);
	
			color_prom.inc();
		}
	
		/* color_prom now points to the beginning of the lookup table */
	
		/* chars use colors 0-15 */
		for (i = 0;i < TOTAL_COLORS(0);i++)
			COLOR(colortable,0,i ^ 3,color_prom.readinc() & 0x0f);
	
		/* sprites use colors 16-31 */
		for (i = 0;i < TOTAL_COLORS(1);i++)
			COLOR(colortable,1,i ^ 3, (color_prom.readinc() & 0x0f) + 0x10);
            }
        };
	
	public static WriteHandlerPtr wiping_flipscreen_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (flipscreen != (data & 1))
		{
			flipscreen = (data & 1);
			memset(dirtybuffer,1,videoram_size[0]);
		}
	} };
	
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr wiping_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
	
		for (offs = videoram_size[0] - 1; offs > 0; offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				int mx,my,sx,sy;
	
				dirtybuffer[offs] = 0;
	
		        mx = offs % 32;
				my = offs / 32;
	
				if (my < 2)
				{
					sx = my + 34;
					sy = mx - 2;
				}
				else if (my >= 30)
				{
					sx = my - 30;
					sy = mx - 2;
				}
				else
				{
					sx = mx + 2;
					sy = my - 2;
				}
	
				if (flipscreen != 0)
				{
					sx = 35 - sx;
					sy = 27 - sy;
				}
	
				drawgfx(tmpbitmap,Machine.gfx[0],
						videoram.read(offs),
						colorram.read(offs) & 0x3f,
						flipscreen,flipscreen,
						sx*8,sy*8,
						Machine.visible_area,TRANSPARENCY_NONE,0);
	        	}
		}
		copybitmap(bitmap,tmpbitmap,0,0,0,0,Machine.visible_area,TRANSPARENCY_NONE,0);
	
		/* Note, we're counting up on purpose ! */
		/* This way the vacuum cleaner is always on top */
		for (offs = 0x0; offs < 128; offs += 2) {
			int sx,sy,flipx,flipy,otherbank;
	
			sx = spriteram.read(offs+0x100+1) + ((spriteram.read(offs+0x81) & 0x01) << 8) - 40;
			sy = 224 - spriteram.read(offs+0x100);
	
			otherbank = spriteram.read(offs+0x80) & 0x01;
	
			flipy = spriteram.read(offs) & 0x40;
			flipx = spriteram.read(offs) & 0x80;
	
			if (flipscreen != 0)
			{
				sy = 208 - sy;
				flipx = flipx!=0?0:1;
				flipy = flipy!=0?0:1;
			}
	
			drawgfx(bitmap,Machine.gfx[1],
				(spriteram.read(offs) & 0x3f) + 64 * otherbank,
				spriteram.read(offs+1) & 0x3f,
				flipx,flipy,
				sx,sy,
				Machine.visible_area,TRANSPARENCY_COLOR,0x1f);
		}
	
		/* redraw high priority chars */
		for (offs = videoram_size[0] - 1; offs > 0; offs--)
		{
			if ((colorram.read(offs) & 0x80) != 0)
			{
				int mx,my,sx,sy;
	
		        mx = offs % 32;
				my = offs / 32;
	
				if (my < 2)
				{
					sx = my + 34;
					sy = mx - 2;
				}
				else if (my >= 30)
				{
					sx = my - 30;
					sy = mx - 2;
				}
				else
				{
					sx = mx + 2;
					sy = my - 2;
				}
	
				if (flipscreen != 0)
				{
					sx = 35 - sx;
					sy = 27 - sy;
				}
	
				drawgfx(bitmap,Machine.gfx[0],
						videoram.read(offs),
						colorram.read(offs) & 0x3f,
						flipscreen,flipscreen,
						sx*8,sy*8,
						Machine.visible_area,TRANSPARENCY_NONE,0);
	        	}
		}
	
	
	/*#if 0
	{
		int i,j;
		extern unsigned char *wiping_soundregs;
	
		for (i = 0;i < 8;i++)
		{
			for (j = 0;j < 8;j++)
			{
				char buf[40];
				sprintf(buf,"%01x",wiping_soundregs[i*8+j]&0xf);
				ui_text(buf,j*10,i*8);
			}
		}
	
		for (i = 0;i < 8;i++)
		{
			for (j = 0;j < 8;j++)
			{
				char buf[40];
				sprintf(buf,"%01x",wiping_soundregs[0x2000+i*8+j]>>4);
				ui_text(buf,j*10,80+i*8);
			}
		}
	}
	#endif*/
	} };
}
