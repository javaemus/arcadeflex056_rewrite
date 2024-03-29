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
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;

import static WIP2.mame056.vidhrdw.generic.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;

public class bosco
{
	
	
	public static int MAX_STARS = 250;
	public static int STARS_COLOR_BASE = 32;
	
	public static UBytePtr bosco_staronoff = new UBytePtr();
	public static UBytePtr bosco_starblink = new UBytePtr();
	static int stars_scrollx;
	static int stars_scrolly;
	static int bosco_scrollx,bosco_scrolly;
	static int bosco_starcontrol;
	static int flipscreen;
	static int displacement;
	
	
	public static class star
	{
		public int x,y,col,set;
	};
	static star[] stars=new star[MAX_STARS];
        static {
            for (int i = 0 ; i<MAX_STARS ; i++)
                stars[i]=new star();
        }
	static int total_stars;
	
	public static int VIDEO_RAM_SIZE = 0x400;
	
	public static UBytePtr bosco_videoram2=new UBytePtr(), bosco_colorram2=new UBytePtr();
	public static UBytePtr bosco_radarx=new UBytePtr(), bosco_radary=new UBytePtr(), bosco_radarattr=new UBytePtr();
	public static int[] bosco_radarram_size = new int[1];
												/* to speed up video refresh */
	static UBytePtr dirtybuffer2=new UBytePtr();	/* keep track of modified portions of the screen */
												/* to speed up video refresh */
	static mame_bitmap tmpbitmap1;
	
	
	
	static rectangle spritevisiblearea = new rectangle
	(
		0*8+3, 28*8-1,
		0*8, 28*8-1
        );
	
	static rectangle spritevisibleareaflip = new rectangle
	(
		8*8, 36*8-1-3,
		0*8, 28*8-1
        );
	
	
	static rectangle radarvisiblearea = new rectangle
	(
		28*8, 36*8-1,
		0*8, 28*8-1
        );
	
	static rectangle radarvisibleareaflip = new rectangle
	(
		0*8, 8*8-1,
		0*8, 28*8-1
        );
	
	
	
	public static VhConvertColorPromPtr bosco_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                int i;
                
                for (i = 0;i < 32;i++)
		{
			int bit0,bit1,bit2;
	
	
			bit0 = (color_prom.read(31-i) >> 0) & 0x01;
			bit1 = (color_prom.read(31-i) >> 1) & 0x01;
			bit2 = (color_prom.read(31-i) >> 2) & 0x01;
			palette[3*i] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
			bit0 = (color_prom.read(31-i) >> 3) & 0x01;
			bit1 = (color_prom.read(31-i) >> 4) & 0x01;
			bit2 = (color_prom.read(31-i) >> 5) & 0x01;
			palette[3*i + 1] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
			bit0 = 0;
			bit1 = (color_prom.read(31-i) >> 6) & 0x01;
			bit2 = (color_prom.read(31-i) >> 7) & 0x01;
			palette[3*i + 2] = (char) (0x21 * bit0 + 0x47 * bit1 + 0x97 * bit2);
		}
	
		/* characters / sprites */
		for (i = 0;i < 64*4;i++)
		{
			colortable[i] = (char) (15 - (color_prom.read(i + 32) & 0x0f));	/* chars */
			colortable[i+64*4] = (char) (15 - (color_prom.read(i + 32) & 0x0f) + 0x10);	/* sprites */
			if (colortable[i+64*4] == 0x10) colortable[i+64*4] = 0;	/* preserve transparency */
		}
	
		/* radar dots lookup table */
		/* they use colors 0-3, I think */
		for (i = 0;i < 4;i++)
			COLOR(colortable,2,i,i);
	
		/* now the stars */
		for (i = 32;i < 32 + 64;i++)
		{
			int bits;
			int map[] = { 0x00, 0x88, 0xcc, 0xff };
	
			bits = ((i-32) >> 0) & 0x03;
			palette[3*i] = (char) map[bits];
			bits = ((i-32) >> 2) & 0x03;
			palette[3*i + 1] = (char) map[bits];
			bits = ((i-32) >> 4) & 0x03;
			palette[3*i + 2] = (char) map[bits];
		}
            }
        };
		
	public static int TOTAL_COLORS(int gfxn){
            return (Machine.gfx[gfxn].total_colors * Machine.gfx[gfxn].color_granularity);
        }
        
	public static void COLOR(char[] colortable, int gfxn, int offs, int value){
            colortable[Machine.drv.gfxdecodeinfo[gfxn].color_codes_start + offs] = (char) value;
        }
        
	public static VhStartPtr bosco_vh_start = new VhStartPtr() { public int handler() 
	{
		int generator;
		int x,y;
		int set = 0;
	
		if (generic_vh_start.handler()!= 0)
			return 1;
	
		if ((dirtybuffer2 = new UBytePtr(videoram_size[0])) == null)
			return 1;
		memset(dirtybuffer2,1,videoram_size[0]);
	
		if ((tmpbitmap1 = bitmap_alloc(32*8,32*8)) == null)
		{
			dirtybuffer2 = null;
			generic_vh_stop.handler();
			return 1;
		}
	
		/* precalculate the star background */
		/* this comes from the Galaxian hardware, Bosconian is probably different */
		total_stars = 0;
		generator = 0;
	
		for (x = 255;x >= 0;x--)
		{
			for (y = 511;y >= 0;y--)
			{
				int bit1,bit2;
	
	
				generator <<= 1;
				bit1 = (~generator >> 17) & 1;
				bit2 = (generator >> 5) & 1;
	
				if ((bit1 ^ bit2) != 0) generator |= 1;
	
				if ((x >= Machine.visible_area.min_x) &&
						(x <= Machine.visible_area.max_x) &&
						(((~generator >> 16) & 1)!=0) &&
						(generator & 0xff) == 0xff)
				{
					int color;
	
					color = (~(generator >> 8)) & 0x3f;
					if ((color!=0) && total_stars < MAX_STARS)
					{
						stars[total_stars].x = x;
						stars[total_stars].y = y;
						stars[total_stars].col = Machine.pens.read(color + STARS_COLOR_BASE);
						stars[total_stars].set = set;
						if (++set > 3)
							set = 0;
	
						total_stars++;
					}
				}
			}
		}
		bosco_staronoff.write( 1 );
	
		displacement = 1;
	
		return 0;
	} };
	
	
	/***************************************************************************
	
	  Stop the video hardware emulation.
	
	***************************************************************************/
	public static VhStopPtr bosco_vh_stop = new VhStopPtr() { public void handler() 
	{
		bitmap_free(tmpbitmap1);
		dirtybuffer2 = null;
		generic_vh_stop.handler();
	} };
	
	
	
	public static WriteHandlerPtr bosco_videoram2_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (bosco_videoram2.read(offset) != data)
		{
			dirtybuffer2.write(offset, 1);
	
			bosco_videoram2.write(offset, data);
		}
	} };
	
	
	
	public static WriteHandlerPtr bosco_colorram2_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (bosco_colorram2.read(offset) != data)
		{
			dirtybuffer2.write(offset, 1);
	
			bosco_colorram2.write(offset, data);
		}
	} };
	
	
	public static WriteHandlerPtr bosco_flipscreen_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (flipscreen != (~data & 1))
		{
			flipscreen = ~data & 1;
			memset(dirtybuffer,1,videoram_size[0]);
			memset(dirtybuffer2,1,videoram_size[0]);
		}
	} };
	
	public static WriteHandlerPtr bosco_scrollx_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		bosco_scrollx = data;
	} };
	
	public static WriteHandlerPtr bosco_scrolly_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		bosco_scrolly = data;
	} };
	
	public static WriteHandlerPtr bosco_starcontrol_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		bosco_starcontrol = data;
	} };
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr bosco_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs,sx,sy;
	
	
		/* for every character in the Video RAM, check if it has been modified */
		/* since last time and update it accordingly. */
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer2.read(offs) != 0)
			{
				int flipx,flipy;
	
	
				dirtybuffer2.write(offs, 0);
	
				sx = offs % 32;
				sy = offs / 32;
				flipx = ~bosco_colorram2.read(offs) & 0x40;
				flipy = bosco_colorram2.read(offs) & 0x80;
				if (flipscreen != 0)
				{
					sx = 31 - sx;
					sy = 31 - sy;
					flipx = flipx!=0 ? 0 : 1;
					flipy = flipy!=0 ? 0 : 1;
				}
	
				drawgfx(tmpbitmap1,Machine.gfx[0],
						bosco_videoram2.read(offs),
						bosco_colorram2.read(offs) & 0x3f,
						flipx,flipy,
						8*sx,8*sy,
						null,TRANSPARENCY_NONE,0);
			}
		}
	
		/* update radar */
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				int flipx,flipy;
	
	
				dirtybuffer[offs] = 0;
	
				sx = (offs % 32) ^ 4;
				sy = offs / 32 - 2;
				flipx = ~colorram.read(offs) & 0x40;
				flipy = colorram.read(offs) & 0x80;
				if (flipscreen != 0)
				{
					sx = 7 - sx;
					sy = 27 - sy;
					flipx = flipx!=0 ? 0 : 1;
					flipy = flipy!=0 ? 0 : 1;
				}
	
				drawgfx(tmpbitmap,Machine.gfx[0],
						videoram.read(offs),
						colorram.read(offs) & 0x3f,
						flipx,flipy,
						8*sx,8*sy,
						radarvisibleareaflip,TRANSPARENCY_NONE,0);
			}
		}
	
	
		fillbitmap(bitmap,Machine.pens.read(0),Machine.visible_area);
	
	
		/* draw the sprites */
		for (offs = 0;offs < spriteram_size[0];offs += 2)
		{
			sx = spriteram.read(offs + 1) - displacement;
                        if (flipscreen != 0) sx += 32;
			sy = 225 - spriteram_2.read(offs) - displacement;
	
			drawgfx(bitmap,Machine.gfx[1],
					(spriteram.read(offs) & 0xfc) >> 2,
					spriteram_2.read(offs + 1) & 0x3f,
					spriteram.read(offs) & 1,spriteram.read(offs) & 2,
					sx,sy,
					flipscreen!=0 ? spritevisibleareaflip : spritevisiblearea,TRANSPARENCY_COLOR,0);
		}
	
	
		/* copy the temporary bitmap to the screen */
		{
			int scrollx,scrolly;
	
	
			if (flipscreen != 0)
			{
				scrollx = (bosco_scrollx +32);//- 3*displacement) + 32;
				scrolly = (bosco_scrolly + 16) - 32;
			}
			else
			{
				scrollx = -(bosco_scrollx);
				scrolly = -(bosco_scrolly + 16);
			}
	
			copyscrollbitmap(bitmap,tmpbitmap1,1,new int[]{scrollx},1,new int[]{scrolly},Machine.visible_area,TRANSPARENCY_COLOR,0);
		}
	
	
		/* radar */
		if (flipscreen != 0)
			copybitmap(bitmap,tmpbitmap,0,0,0,0,radarvisibleareaflip,TRANSPARENCY_NONE,0);
		else
			copybitmap(bitmap,tmpbitmap,0,0,28*8,0,radarvisiblearea,TRANSPARENCY_NONE,0);
	
	
		/* draw the dots on the radar and the bullets */
		for (offs = 0; offs < bosco_radarram_size[0];offs++)
		{
			int x,y;
	
	
			x = bosco_radarx.read(offs) + ((~bosco_radarattr.read(offs) & 0x01) << 8) - 2;
			y = 235 - bosco_radary.read(offs);
			if (flipscreen != 0)
			{
				x -= 1;
				y += 2;
			}
	
			drawgfx(bitmap,Machine.gfx[2],
					((bosco_radarattr.read(offs) & 0x0e) >> 1) ^ 0x07,
					0,
					flipscreen,flipscreen,
					x,y,
					Machine.visible_area,TRANSPARENCY_PEN,3);
		}
	
	
		/* draw the stars */
		if ((bosco_staronoff.read() & 1) == 0)
		{
			int bpen;
	
			bpen = Machine.pens.read(0);
			for (offs = 0;offs < total_stars;offs++)
			{
				int x,y;
				int set;
				int starset[][] = {{0,3},{0,1},{2,3},{2,1}};
	
				x = (stars[offs].x + stars_scrollx) % 224;
				y = (stars[offs].y + stars_scrolly) % 224;
	
				set = (bosco_starblink.read(0) & 1) + ((bosco_starblink.read(1) & 1) << 1);
	
				if (((stars[offs].set == starset[set][0]) ||
					 (stars[offs].set == starset[set][1])))
				{
					if (read_pixel.handler(bitmap, x, y) == bpen)
					{
						plot_pixel.handler(bitmap, x, y, stars[offs].col);
					}
				}
			}
		}
	} };
	
	public static void bosco_vh_interrupt()
	{
		int speedsx[] = { -1, -2, -3, 0, 3, 2, 1, 0 };
		int speedsy[] = { 0, -1, -2, -3, 0, 3, 2, 1 };
	
		stars_scrollx += speedsx[bosco_starcontrol & 7];
		stars_scrolly += speedsy[(bosco_starcontrol & 56) >> 3];
	}
}
