/***************************************************************************

Functions to emulate the video hardware of the machine.

 Video hardware of this hardware is almost similar with "mexico86". So,
 most routines are derived from mexico86 driver.

***************************************************************************/


/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.vidhrdw;

import static WIP2.arcadeflex056.fucPtr.*;

import static WIP2.common.ptr.*;
import static WIP2.common.libc.cstring.*;
import static WIP2.common.libc.cstdio.sprintf;
import static WIP2.mame056.usrintrf.usrintf_showmessage;

import static WIP2.mame056.common.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.memory.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.sound.mixer.*;
import static WIP2.mame056.vidhrdw.generic.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP2.mame056.cpuintrfH.*;

public class exzisus
{
	
	
	public static UBytePtr exzisus_videoram0 = new UBytePtr();
	public static UBytePtr exzisus_videoram1 = new UBytePtr();
	public static UBytePtr exzisus_objectram0 = new UBytePtr();
	public static UBytePtr exzisus_objectram1 = new UBytePtr();
	public static int[] exzisus_objectram_size0=new int[1];
	public static int[] exzisus_objectram_size1=new int[1];
	
	
	
	/***************************************************************************
	  Memory handlers
	***************************************************************************/
	
	public static ReadHandlerPtr exzisus_videoram_0_r = new ReadHandlerPtr() {
            public int handler(int offset) {
                return exzisus_videoram0.read(offset);
            }
        };
	
	public static ReadHandlerPtr exzisus_videoram_1_r = new ReadHandlerPtr() {
            public int handler(int offset) {
		return exzisus_videoram1.read(offset);
            }
	};
	
	
	public static ReadHandlerPtr exzisus_objectram_0_r = new ReadHandlerPtr() {
            public int handler(int offset) {
		return exzisus_objectram0.read(offset);
            }
	};
	
	
	public static ReadHandlerPtr exzisus_objectram_1_r = new ReadHandlerPtr() {
            public int handler(int offset) {
		return exzisus_objectram1.read(offset);
            }
	};
	
	
	public static WriteHandlerPtr exzisus_videoram_0_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		exzisus_videoram0.write(offset, data);
	} };
	
	
	public static WriteHandlerPtr exzisus_videoram_1_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		exzisus_videoram1.write(offset, data);
	} };
	
	
	public static WriteHandlerPtr exzisus_objectram_0_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		exzisus_objectram0.write(offset, data);
	} };
	
	
	public static WriteHandlerPtr exzisus_objectram_1_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		exzisus_objectram1.write(offset, data);
	} };
	
	
	/***************************************************************************
	  Screen refresh
	***************************************************************************/
	
	public static VhUpdatePtr exzisus_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
		int sx, sy, xc, yc;
		int gfx_num, gfx_attr, gfx_offs;
	
		/* Is this correct ? */
		fillbitmap(bitmap, Machine.pens[1023], Machine.visible_area);
	
		/* ---------- 1st TC0010VCU ---------- */
		sx = 0;
		for (offs = 0 ; offs < exzisus_objectram_size0[0] ; offs += 4)
	    {
			int height;
	
			/* Skip empty sprites. */
			if ( ((exzisus_objectram0.read(offs) == 0)) )
			{
				continue;
			}
	
			gfx_num = exzisus_objectram0.read(offs + 1);
			gfx_attr = exzisus_objectram0.read(offs + 3);
	
			if ((gfx_num & 0x80) == 0)	/* 16x16 sprites */
			{
				gfx_offs = ((gfx_num & 0x7f) << 3);
				height = 2;
	
				sx = exzisus_objectram0.read(offs + 2);
				sx |= (gfx_attr & 0x40) << 2;
			}
			else	/* tilemaps (each sprite is a 16x256 column) */
			{
				gfx_offs = ((gfx_num & 0x3f) << 7) + 0x0400;
				height = 32;
	
				if ((gfx_num & 0x40) != 0)			/* Next column */
				{
					sx += 16;
				}
				else
				{
					sx = exzisus_objectram0.read(offs + 2);
					sx |= (gfx_attr & 0x40) << 2;
				}
			}
	
			sy = 256 - (height << 3) - (exzisus_objectram0.read(offs));
	
			for (xc = 0 ; xc < 2 ; xc++)
			{
				int goffs = gfx_offs;
				for (yc = 0 ; yc < height ; yc++)
				{
					int code, color, x, y;
	
					code  = (exzisus_videoram0.read(goffs + 1) << 8) | exzisus_videoram0.read(goffs);
					color = (exzisus_videoram0.read(goffs + 1) >> 6) | (gfx_attr & 0x0f);
					x = (sx + (xc << 3)) & 0xff;
					y = (sy + (yc << 3)) & 0xff;
	
					drawgfx(bitmap, Machine.gfx[0],
							code & 0x3fff,
							color,
							0, 0,
							x, y,
							Machine.visible_area, TRANSPARENCY_PEN, 15);
					goffs += 2;
				}
				gfx_offs += height << 1;
			}
		}
	
		/* ---------- 2nd TC0010VCU ---------- */
		sx = 0;
		for (offs = 0 ; offs < exzisus_objectram_size1[0] ; offs += 4)
	    {
			int height;
	
			/* Skip empty sprites. */
			if ( ((exzisus_objectram1.read(offs) == 0)) )
			{
				continue;
			}
	
			gfx_num = exzisus_objectram1.read(offs + 1);
			gfx_attr = exzisus_objectram1.read(offs + 3);
	
			if ((gfx_num & 0x80) == 0)	/* 16x16 sprites */
			{
				gfx_offs = ((gfx_num & 0x7f) << 3);
				height = 2;
	
				sx = exzisus_objectram1.read(offs + 2);
				sx |= (gfx_attr & 0x40) << 2;
			}
			else	/* tilemaps (each sprite is a 16x256 column) */
			{
				gfx_offs = ((gfx_num & 0x3f) << 7) + 0x0400;	///
				height = 32;
	
				if ((gfx_num & 0x40) != 0)			/* Next column */
				{
					sx += 16;
				}
				else
				{
					sx = exzisus_objectram1.read(offs + 2);
					sx |= (gfx_attr & 0x40) << 2;
				}
			}
			sy = 256 - (height << 3) - (exzisus_objectram1.read(offs));
	
			for (xc = 0 ; xc < 2 ; xc++)
			{
				int goffs = gfx_offs;
				for (yc = 0 ; yc < height ; yc++)
				{
					int code, color, x, y;
	
					code  = (exzisus_videoram1.read(goffs + 1) << 8) | exzisus_videoram1.read(goffs);
					color = (exzisus_videoram1.read(goffs + 1) >> 6) | (gfx_attr & 0x0f);
					x = (sx + (xc << 3)) & 0xff;
					y = (sy + (yc << 3)) & 0xff;
	
					drawgfx(bitmap, Machine.gfx[1],
							code & 0x3fff,
							color,
							0, 0,
							x, y,
							Machine.visible_area, TRANSPARENCY_PEN, 15);
					goffs += 2;
				}
				gfx_offs += height << 1;
			}
		}
	} };
	
	
}
