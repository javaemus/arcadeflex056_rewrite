/***************************************************************************

  Atari Video Pinball Video Hardware

  Functions to emulate the video hardware of the machine.

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.vidhrdw;

import static WIP2.arcadeflex056.fucPtr.*;

import static WIP2.common.libc.cstring.*;
import static WIP2.common.ptr.*;
import static WIP2.common.libc.expressions.*;

import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.palette.*;

import static WIP2.mame056.vidhrdw.generic.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP.mame056.machine.videopin.*;
import static WIP2.mame056.artworkH.*;
import static WIP2.mame056.artwork.*;

public class videopin
{
	
	
	/* Artwork */
	public static artwork_info videopin_backdrop = null;
	
	/* machine/videopin.c */
	
	/* Playfield and ball clipping area
	 * This '+8' is to adjust display to the approximate backdrop
	 */
	static rectangle vpclip = new rectangle( (360-296)/2, (360-296)/2 + 296,
								(312-256)/2 +8, (312-256)/2 +8 +256 );
	
	
	
	public static VhUpdatePtr videopin_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
		int[] balloffs=new int[4];
                int offsc=0;
		int charcode;
		int sx,sy,tsx, tsy;
		rectangle aclip = new rectangle();
	/*	int x,rx,ry;
		char dbuf[50];*/
	
		/*logerror("vh_screenrefresh, %02x\n", full_refresh); */
	
		if (full_refresh != 0)
		{
			memset(dirtybuffer,1,videoram_size[0]);
		}
	
	    /* For every character in the Video RAM, check if it has been modified
		 * since last time and update it accordingly.
		 *
		 * Video is generated from playfield ram with 8x8 stamps coded like this:
		 * --xxxxxx address one of the 64 ROM gfx
		 * -x------ flip x stamp flag
		 * x------- indicate the presence of the ball (on a 2x2 stamps window)
		 *
		 * Also playfield is weirdly cut in two which explains the sx, sy test
		 * Playfield window size is 296x256
		 * X and Y are referring to the screen in it's non final orientation (ROT0)
		 */
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (full_refresh!=0 || dirtybuffer[offs]!=0)
			{
				dirtybuffer[offs]=0;
	
				charcode = videoram.read(offs) & 0x3F;
	
				/* Correct coordinates for cut-in-half display */
				sx = 8 * (offs / 32);
				sy = 8 * (offs % 32);
				if (sx < 128) sx += 256;
				else sx -= 128;
	
				/* To shift PF in place on the backdrop */
				sx += (360-296)/2;
				sy += (312-256)/2 +8; /* This '+8' is to adjust display to the approximate backdrop */
	
				/* Draw Artwork */
                                if (videopin_backdrop != null)
				{
					/* Refreshed stamp/tile clipping area */
					aclip.min_x = sx;
					aclip.max_x = sx+7;
					aclip.min_y = sy;
					aclip.max_y = sy+7;
					copybitmap(tmpbitmap,videopin_backdrop.artwork,0,0,0,0,aclip,TRANSPARENCY_NONE,0);
				}
	
				if ((videoram.read(offs) & 0x40) != 0)
				{
					drawgfx(tmpbitmap,Machine.gfx[0],
						charcode, 1,
						0,1,sx,sy,
						vpclip,videopin_backdrop!=null?TRANSPARENCY_PEN:TRANSPARENCY_NONE,0);
				}
				else
				{
					drawgfx(tmpbitmap,Machine.gfx[0],
						charcode, 1,
						0,0,sx,sy,
						vpclip,videopin_backdrop!=null?TRANSPARENCY_PEN:TRANSPARENCY_NONE,0);
				}
			}
	
			/* Bit 7 indicate presence of the ball window in four tiles.
			 * offsc test is necessary for selftest mode
			 * where there are more than 4 tiles having the ball bit
			 */
			if ((videoram.read(offs) & 0x80) != 0)
			{
				if (offsc < 4)
				{
					balloffs[offsc] = offs;
					/*logerror("vh_screenrefresh, drawball #%1d:%x\n", offsc-1,offs); */
				}
				offsc++;
			}
		}
	
	
		/* We draw ball on the top of the current playfield display
		 * Ball_position bits:
		 * -----xxx horizontal  /- 0:left half of the 16x16 window
		 * ----x--- horizontal /-- 1:right half of the 16x16 window
		 * xxxx---- vertical (16 line addresses)
		 * We arbitraly choosed to draw ball from its fourth offset
		 * Ball is always drawn even if out of the visible area of playfield
		 * except if there are more than four tiles with ball bit (selftest)
		 */
		if (offsc==4)
		{
	
			/* Debug purpose : draw ball window */
			/*for(offsc=0; offsc<4; offsc++) */
			/*{ */
			/*	sx = 8 * (balloffs[offsc] / 32); */
			/*	sy = 8 * (balloffs[offsc] % 32); */
			/*	if (sx < 128) sx += 256; */
			/*	else sx -= 128; */
			/*	sx += (360-296)/2; */
			/*	sy += (312-256)/2 +8; */
			/*	drawgfx(tmpbitmap,Machine.gfx[0], */
			/*		48+offsc,1, */
			/*		0,1,sx,sy, */
			/*		&Machine.drv.visible_area,TRANSPARENCY_NONE,0); */
			/*} */
	
			/* Exception: since the display in cut in two non sequential half,
			 * when the ball window is split between the two areas,
			 * the offset we draw the ball from changes
			 */
			if (balloffs[1]-balloffs[3]>32) offsc=1;
			else offsc=3;
	
			/* Correct coordinates for cut-in-half display */
			sx = 8 * (balloffs[offsc] / 32);
			sy = 8 * (balloffs[offsc] % 32);
			if (sx < 128) sx += 256;
			else sx -= 128;
	
			/* To shift ball in place on the backdrop */
			sx += (360-296)/2;
			sy += (312-256)/2 +8;
	
			/*rx = sx; ry = sy;	// Debug purpose */
	
			tsx = ball_position & 0x0F;
			if (tsx != 0) sx += 16-tsx;
	
			tsy = (ball_position & 0xF0) >> 4;
			if (tsy != 0) sy += 16-tsy;
	
			drawgfx(tmpbitmap,Machine.gfx[1],
				0,1,
				0,0,sx,sy,
				vpclip,videopin_backdrop!=null?TRANSPARENCY_PEN:TRANSPARENCY_NONE,0);
	
			/* Debug purpose: draw ball display information */
			/*logerror("x=%03d,  y=%03d,   ball position=%02x\n", sx, sy, ball_position); */
			/*sprintf(dbuf, "x=%03d y=%03d rx=%03d ry=%03d ball=%02x offs=%04x", */
			/*		sx, sy, rx, ry, ball_position, balloffs[offsc]); */
			/*for (x = 0;x < 43;x++) */
			/*	drawgfx(tmpbitmap,Machine.uifont,dbuf[x],DT_COLOR_WHITE, */
			/*			1,0,350,6*x,&Machine.drv.visible_area,TRANSPARENCY_NONE,0); */
		}
	
		/* copy the temporary bitmap to the destination bitmap */
		copybitmap(bitmap,tmpbitmap,0,0,0,0,Machine.visible_area,TRANSPARENCY_NONE,0);
	} };
	
	
	public static VhStartPtr videopin_vh_start = new VhStartPtr() { public int handler() 
	{
		if (generic_vh_start.handler()!=0)
			return 1;
	
		if (videopin_backdrop != null)
		{
			copybitmap(tmpbitmap,videopin_backdrop.artwork,0,0,0,0,Machine.visible_area,TRANSPARENCY_NONE,0);
		}
	
		return 0;
	} };
	
	
	public static VhStopPtr videopin_vh_stop = new VhStopPtr() { public void handler() 
	{
		generic_vh_stop.handler();
	
		/* Free Artwork */
		if (videopin_backdrop != null)
			artwork_free(videopin_backdrop);
                videopin_backdrop = null;
	} };
	
}
