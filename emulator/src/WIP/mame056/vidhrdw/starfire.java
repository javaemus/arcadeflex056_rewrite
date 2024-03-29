/***************************************************************************

	Star Fire video system

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.vidhrdw;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.libc.cstring.memset;

import static WIP2.common.ptr.*;

import static WIP2.mame056.common.*;
import static mame056.palette.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;

import static WIP2.mame056.vidhrdw.generic.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP.mame056.drivers.starfire.starfire_videoram;
import static WIP.mame056.drivers.starfire.starfire_colorram;

public class starfire
{
	
        /* local allocated storage */
	public static UBytePtr scanline_dirty = new UBytePtr();
	
	static int starfire_vidctrl;
	static int starfire_vidctrl1;
	static int starfire_color;
	
	
	
	/*************************************
	 *
	 *	Initialize the video system
	 *
	 *************************************/
	
	public static VhStartPtr starfire_vh_start = new VhStartPtr() { public int handler() 
	{
		/* make a temporary bitmap */
		tmpbitmap = bitmap_alloc(Machine.drv.screen_width, Machine.drv.screen_height);
		if (tmpbitmap == null)
			return 1;
	
		/* make a dirty array */
		scanline_dirty = new UBytePtr(256);
		if (scanline_dirty == null)
		{
			bitmap_free(tmpbitmap);
			return 1;
		}
	
		/* reset videoram */
		memset(starfire_videoram, 0, 0x2000);
		memset(starfire_colorram, 0, 0x2000);
		memset(scanline_dirty, 1, 256);
	
		return 0;
	} };
	
	
	
	/*************************************
	 *
	 *	Tear down the video system
	 *
	 *************************************/
	
	public static VhStopPtr starfire_vh_stop = new VhStopPtr() { public void handler() 
	{
		bitmap_free(tmpbitmap);
		scanline_dirty = null;
	} };
	
	
	
	/*************************************
	 *
	 *	Video control writes
	 *
	 *************************************/
	
	public static WriteHandlerPtr starfire_vidctrl_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
	    starfire_vidctrl = data;
	} };
	
	public static WriteHandlerPtr starfire_vidctrl1_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
	    starfire_vidctrl1 = data;
	} };
	
	
	
	/*************************************
	 *
	 *	Color RAM read/writes
	 *
	 *************************************/
	
	public static WriteHandlerPtr starfire_colorram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		/* handle writes to the pseudo-color RAM */
		if ((offset & 0xe0) == 0)
		{
			int palette_index = (offset & 0x1f) | ((offset & 0x200) >> 4);
			int r = ((data << 1) & 0x06) | ((offset >> 8) & 0x01);
			int b = (data >> 2) & 0x07;
			int g = (data >> 5) & 0x07;
	
			/* set RAM regardless */
			starfire_colorram.write(offset & ~0x100, data);
			starfire_colorram.write(offset |  0x100, data);
	
			/* don't modify the palette unless the TRANS bit is set */
			starfire_color = data & 0x1f;
			if ((starfire_vidctrl1 & 0x40) == 0)
				return;
	
			/* modify the pen */
			r = (r << 5) | (r << 2) | (r >> 1);
			b = (b << 5) | (b << 2) | (b >> 1);
			g = (g << 5) | (g << 2) | (g >> 1);
			palette_set_color(palette_index, r, g, b);
		}
	
		/* handle writes to the rest of color RAM */
		else
		{
			/* set RAM based on CDRM */
			starfire_colorram.write(offset, (starfire_vidctrl1 & 0x80)!=0 ? starfire_color : (data & 0x1f));
			scanline_dirty.write(offset & 0xff, 1);
			starfire_color = data & 0x1f;
		}
	} };
	
	public static ReadHandlerPtr starfire_colorram_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return starfire_colorram.read(offset);
	} };
	
	
	
	/*************************************
	 *
	 *	Video RAM read/writes
	 *
	 *************************************/
	
	public static WriteHandlerPtr starfire_videoram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		int sh, lr, dm, ds, mask, d0, dalu;
		int offset1 = offset & 0x1fff;
		int offset2 = (offset + 0x100) & 0x1fff;
	
		/* PROT */
		if (((offset & 0xe0)==0) && ((starfire_vidctrl1 & 0x20)==0))
			return;
	
		/* selector 6A */
		if ((offset & 0x2000) != 0)
		{
			sh = (starfire_vidctrl >> 1) & 0x07;
			lr = starfire_vidctrl & 0x01;
		}
		else
		{
			sh = (starfire_vidctrl >> 5) & 0x07;
			lr = (starfire_vidctrl >> 4) & 0x01;
		}
	
		/* mirror bits 5B/5C/5D/5E */
		dm = data;
		if (lr != 0)
			dm = ((dm & 0x01) << 7) | ((dm & 0x02) << 5) | ((dm & 0x04) << 3) | ((dm & 0x08) << 1) |
			     ((dm & 0x10) >> 1) | ((dm & 0x20) >> 3) | ((dm & 0x40) >> 5) | ((dm & 0x80) >> 7);
	
		/* shifters 6D/6E */
		ds = (dm << 8) >> sh;
		mask = 0xff00 >> sh;
	
		/* ROLL */
		if ((offset & 0x1f00) == 0x1f00)
		{
			if ((starfire_vidctrl1 & 0x10) != 0)
				mask &= 0x00ff;
			else
				mask &= 0xff00;
		}
	
		/* ALU 8B/8D */
		d0 = (starfire_videoram.read(offset1) << 8) | starfire_videoram.read(offset2);
		dalu = d0 & ~mask;
		d0 &= mask;
		ds &= mask;
		switch (~starfire_vidctrl1 & 15)
		{
			case 0:		dalu |= ds ^ mask;				break;
			case 1:		dalu |= (ds | d0) ^ mask;		break;
			case 2:		dalu |= (ds ^ mask) & d0;		break;
			case 3:		dalu |= 0;						break;
			case 4:		dalu |= (ds & d0) ^ mask;		break;
			case 5:		dalu |= d0 ^ mask;				break;
			case 6:		dalu |= ds ^ d0;				break;
			case 7:		dalu |= ds & (d0 ^ mask);		break;
			case 8:		dalu |= (ds ^ mask) | d0;		break;
			case 9:		dalu |= (ds ^ d0) ^ mask;		break;
			case 10:	dalu |= d0;						break;
			case 11:	dalu |= ds & d0;				break;
			case 12:	dalu |= mask;					break;
			case 13:	dalu |= ds | (d0 ^ mask);		break;
			case 14:	dalu |= ds | d0;				break;
			case 15:	dalu |= ds;						break;
		}
	
		/* final output */
		starfire_videoram.write(offset1, dalu >> 8);
		starfire_videoram.write(offset2, dalu);
		scanline_dirty.write(offset1 & 0xff, 1);
	
		/* color output */
		if (((offset & 0x2000)==0) && ((starfire_vidctrl1 & 0x80)==0))
		{
			if ((mask & 0xff00) != 0)
				starfire_colorram.write(offset1, starfire_color);
			if ((mask & 0x00ff) != 0)
				starfire_colorram.write(offset2, starfire_color);
		}
	} };
	
	public static ReadHandlerPtr starfire_videoram_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int sh, mask, d0;
		int offset1 = offset & 0x1fff;
		int offset2 = (offset + 0x100) & 0x1fff;
	
		/* selector 6A */
		if ((offset & 0x2000) != 0)
			sh = (starfire_vidctrl >> 1) & 0x07;
		else
			sh = (starfire_vidctrl >> 5) & 0x07;
	
		/* shifters 6D/6E */
		mask = 0xff00 >> sh;
	
		/* ROLL */
		if ((offset & 0x1f00) == 0x1f00)
		{
			if ((starfire_vidctrl1 & 0x10) != 0)
				mask &= 0x00ff;
			else
				mask &= 0xff00;
		}
	
		/* munge the results */
		d0 = (starfire_videoram.read(offset1) & (mask >> 8)) | (starfire_videoram.read(offset2) & mask);
		d0 = (d0 << sh) | (d0 >> (8 - sh));
		return d0 & 0xff;
	} };
	
	
	
	/*************************************
	 *
	 *	Periodic screen refresh callback
	 *
	 *************************************/
	
	public static void starfire_video_update(int scanline, int count)
	{
		UBytePtr pix = new UBytePtr(starfire_videoram, scanline);
		UBytePtr col = new UBytePtr(starfire_colorram, scanline);
		int x, y;
	
		/* update any dirty scanlines in this range */
		for (x = 0; x < 256; x += 8)
		{
			for (y = 0; y < count; y++)
				if ((scanline_dirty.read(scanline + y)) != 0)
				{
					int data = pix.read(y);
					int color = col.read(y);
	
					plot_pixel.handler(tmpbitmap, x + 0, scanline + y, color | ((data >> 2) & 0x20));
					plot_pixel.handler(tmpbitmap, x + 1, scanline + y, color | ((data >> 1) & 0x20));
					plot_pixel.handler(tmpbitmap, x + 2, scanline + y, color | ((data >> 0) & 0x20));
					plot_pixel.handler(tmpbitmap, x + 3, scanline + y, color | ((data << 1) & 0x20));
					plot_pixel.handler(tmpbitmap, x + 4, scanline + y, color | ((data << 2) & 0x20));
					plot_pixel.handler(tmpbitmap, x + 5, scanline + y, color | ((data << 3) & 0x20));
					plot_pixel.handler(tmpbitmap, x + 6, scanline + y, color | ((data << 4) & 0x20));
					plot_pixel.handler(tmpbitmap, x + 7, scanline + y, color | ((data << 5) & 0x20));
				}
	
			pix.inc(256);
			col.inc(256);
		}
	
		/* mark them not dirty anymore */
		for (y = 0; y < count; y++)
			scanline_dirty.write(scanline + y, 0);
	}
	
	
	
	/*************************************
	 *
	 *	Standard screen refresh callback
	 *
	 *************************************/
	
	public static VhUpdatePtr starfire_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		/* copy the bitmap, remapping the colors */
		copybitmap_remap(bitmap, tmpbitmap, 0, 0, 0, 0, Machine.visible_area, TRANSPARENCY_NONE, 0);
	} };
	
	
}
