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
import static WIP.mame056.machine.sprint2.*;

public class sprint2
{
	
	/* machine/sprint2.c */
	
	public static UBytePtr sprint2_horiz_ram = new UBytePtr();
	public static UBytePtr sprint2_vert_car_ram = new UBytePtr();
	
	static mame_bitmap back_vid;
	static mame_bitmap grey_cars_vid;
	static mame_bitmap black_car_vid;
	static mame_bitmap white_car_vid;
	
	public static int WHITE_CAR   = 0;
	public static int BLACK_CAR   = 1;
	public static int GREY_CAR1   = 2;
	public static int GREY_CAR2   = 3;
	
	/***************************************************************************
	***************************************************************************/
	
	public static VhStartPtr sprint2_vh_start = new VhStartPtr() { public int handler() 
	{
		if (generic_vh_start.handler()!=0)
			return 1;
	
		if ((back_vid = bitmap_alloc(16,8)) == null)
		{
			generic_vh_stop.handler();
			return 1;
		}
	
		if ((grey_cars_vid = bitmap_alloc(16,8)) == null)
		{
			bitmap_free(back_vid);
			generic_vh_stop.handler();
			return 1;
		}
	
		if ((black_car_vid = bitmap_alloc(16,8)) == null)
		{
			bitmap_free(back_vid);
			bitmap_free(grey_cars_vid);
			generic_vh_stop.handler();
			return 1;
		}
	
		if ((white_car_vid = bitmap_alloc(16,8)) == null)
		{
			bitmap_free(back_vid);
			bitmap_free(grey_cars_vid);
			bitmap_free(black_car_vid);
			generic_vh_stop.handler();
			return 1;
		}
	
		return 0;
	} };
	
	/***************************************************************************
	***************************************************************************/
	
	public static VhStopPtr sprint2_vh_stop = new VhStopPtr() { public void handler() 
	{
		bitmap_free(back_vid);
		bitmap_free(grey_cars_vid);
		bitmap_free(black_car_vid);
		bitmap_free(white_car_vid);
		generic_vh_stop.handler();
	} };
	
	/***************************************************************************
	sprint2_check_collision
	
	It might seem strange to put the collision-checking routine in vidhrdw.
	However, the way Sprint2 hardware collision-checking works is by sending
	the video signals for the grey cars, white car, black car, black background,
	and white background through a series of logic gates.  This effectively checks
	for collisions at a pixel-by-pixel basis.  So we'll do the same thing, but
	with a little bit of smarts - there can only be collisions where the black
	car and white car are located, so we'll base our checks on these two locations.
	
	We can't just check the color of the main bitmap at a given location, because one
	of our video signals might have overdrawn another one.  So here's what we do:
	1)  Redraw the background, grey cars, black car, and white car into separate
	bitmaps, but clip to where the white car is located.
	2)  Scan through the bitmaps, apply the logic from the logic gates and look
	for a collision (Collision1).
	3)  Redraw the background, grey cars, black car, and white car into separate
	bitmaps, but clip to where the black car is located.
	4)  Scan through the bitmaps, apply the logic from the logic gates, and look
	for a collision (Collision2).
	***************************************************************************/
	
	static void sprint2_check_collision1(mame_bitmap bitmap)
	{
	    int sx,sy,org_x,org_y;
	    rectangle clip = new rectangle();
	    int offs;
	
	    clip.min_x=0;
	    clip.max_x=15;
	    clip.min_y=0;
	    clip.max_y=7;
	
	    /* Clip in relation to the white car. */
	
	    org_x=30*8-sprint2_horiz_ram.read(WHITE_CAR);
	    org_y=31*8-sprint2_vert_car_ram.read(WHITE_CAR*2);
	
	    fillbitmap(back_vid,Machine.pens.read(1),clip);
	    fillbitmap(grey_cars_vid,Machine.pens.read(1),clip);
	    fillbitmap(white_car_vid,Machine.pens.read(1),clip);
	    fillbitmap(black_car_vid,Machine.pens.read(1),clip);
	
	    /* Draw the background - a car can overlap up to 6 background squares. */
	    /* This could be optimized by not drawing all 6 every time. */
	
	    offs=((org_y/8)*32) + ((org_x/8)%32);
	
	    sx = 8 * (offs % 32)-org_x;
	    sy = 8 * (offs / 32)-org_y;
	
	    drawgfx(back_vid,Machine.gfx[0],
	            videoram.read(offs) & 0x3F, (videoram.read(offs) & 0x80)>>7,
				0,0,sx,sy, clip,TRANSPARENCY_NONE,0);
	
	    offs=((org_y/8)*32) + (((org_x+8)/8)%32);
	    sx = 8 * (offs % 32)-org_x;
	    sy = 8 * (offs / 32)-org_y;
	
	    drawgfx(back_vid,Machine.gfx[0],
	            videoram.read(offs) & 0x3F, (videoram.read(offs) & 0x80)>>7,
				0,0,sx,sy, clip,TRANSPARENCY_NONE,0);
	
	    offs=((org_y/8)*32) + (((org_x+16)/8)%32);
	    sx = 8 * (offs % 32)-org_x;
	    sy = 8 * (offs / 32)-org_y;
	
	    drawgfx(back_vid,Machine.gfx[0],
	            videoram.read(offs) & 0x3F, (videoram.read(offs) & 0x80)>>7,
				0,0,sx,sy, clip,TRANSPARENCY_NONE,0);
	
	    offs=(((org_y+8)/8)*32) + ((org_x/8)%32);
	    sx = 8 * (offs % 32)-org_x;
	    sy = 8 * (offs / 32)-org_y;
	
	    drawgfx(back_vid,Machine.gfx[0],
	            videoram.read(offs) & 0x3F, (videoram.read(offs) & 0x80)>>7,
				0,0,sx,sy, clip,TRANSPARENCY_NONE,0);
	
	    offs=(((org_y+8)/8)*32) + (((org_x+8)/8)%32);
	    sx = 8 * (offs % 32)-org_x;
	    sy = 8 * (offs / 32)-org_y;
	
	    drawgfx(back_vid,Machine.gfx[0],
	            videoram.read(offs) & 0x3F, (videoram.read(offs) & 0x80)>>7,
				0,0,sx,sy, clip,TRANSPARENCY_NONE,0);
	
	    offs=(((org_y+8)/8)*32) + (((org_x+16)/8)%32);
	    sx = 8 * (offs % 32)-org_x;
	    sy = 8 * (offs / 32)-org_y;
	
	    drawgfx(back_vid,Machine.gfx[0],
	            videoram.read(offs) & 0x3F, (videoram.read(offs) & 0x80)>>7,
				0,0,sx,sy, clip,TRANSPARENCY_NONE,0);
	
	
	    /* Grey car 1 */
	    sx=30*8-sprint2_horiz_ram.read(GREY_CAR1);
	    sy=31*8-sprint2_vert_car_ram.read(GREY_CAR1*2);
	    sx=sx-org_x;
	    sy=sy-org_y;
	
	    drawgfx(grey_cars_vid,Machine.gfx[1],
	            (sprint2_vert_car_ram.read(GREY_CAR1*2+1)>>3), GREY_CAR1,
	            0,0,sx,sy,clip,TRANSPARENCY_NONE,0);
	
	    /* Grey car 2 */
	    sx=30*8-sprint2_horiz_ram.read(GREY_CAR2);
	    sy=31*8-sprint2_vert_car_ram.read(GREY_CAR2*2);
	    sx=sx-org_x;
	    sy=sy-org_y;
	
	    drawgfx(grey_cars_vid,Machine.gfx[1],
	            (sprint2_vert_car_ram.read(GREY_CAR2*2+1)>>3), GREY_CAR2,
	            0,0,sx,sy,clip,TRANSPARENCY_COLOR,1);
	
	
	    /* Black car */
	    sx=30*8-sprint2_horiz_ram.read(BLACK_CAR);
	    sy=31*8-sprint2_vert_car_ram.read(BLACK_CAR*2);
	    sx=sx-org_x;
	    sy=sy-org_y;
	
	    drawgfx(black_car_vid,Machine.gfx[1],
	            (sprint2_vert_car_ram.read(BLACK_CAR*2+1)>>3), BLACK_CAR,
	            0,0,sx,sy,clip,TRANSPARENCY_NONE,0);
	
	    /* White car */
	    drawgfx(white_car_vid,Machine.gfx[1],
	            (sprint2_vert_car_ram.read(WHITE_CAR*2+1)>>3), WHITE_CAR,
	            0,0,0,0,clip,TRANSPARENCY_NONE,0);
	
	    /* Now check for Collision1 */
	    for (sy=0;sy<8;sy++)
	    {
	        for (sx=0;sx<16;sx++)
	        {
	                if (read_pixel.handler(white_car_vid, sx, sy)==Machine.pens.read(3))
	                {
						int back_pixel;
	
	                    /* Condition 1 - white car = black car */
	                    if (read_pixel.handler(black_car_vid, sx, sy)==Machine.pens.read(0))
	                        sprint2_collision1_data|=0x40;
	
	                    /* Condition 2 - white car = grey cars */
	                    if (read_pixel.handler(grey_cars_vid, sx, sy)==Machine.pens.read(2))
	                        sprint2_collision1_data|=0x40;
	
	                    back_pixel = read_pixel.handler(back_vid, sx, sy);
	
	                    /* Condition 3 - white car = black playfield (oil) */
	                    if (back_pixel==Machine.pens.read(0))
	                        sprint2_collision1_data|=0x40;
	
	                    /* Condition 4 - white car = white playfield (track) */
	                    if (back_pixel==Machine.pens.read(3))
	                        sprint2_collision1_data|=0x80;
	               }
	        }
	    }
	
	}
	
	static void sprint2_check_collision2(mame_bitmap bitmap)
	{
	
	    int sx,sy,org_x,org_y;
	    rectangle clip = new rectangle();
	    int offs;
	
	    clip.min_x=0;
	    clip.max_x=15;
	    clip.min_y=0;
	    clip.max_y=7;
	
	    /* Clip in relation to the black car. */
	
	    org_x=30*8-sprint2_horiz_ram.read(BLACK_CAR);
	    org_y=31*8-sprint2_vert_car_ram.read(BLACK_CAR*2);
	
	    fillbitmap(back_vid,Machine.pens.read(1),clip);
	    fillbitmap(grey_cars_vid,Machine.pens.read(1),clip);
	    fillbitmap(white_car_vid,Machine.pens.read(1),clip);
	    fillbitmap(black_car_vid,Machine.pens.read(1),clip);
	
	    /* Draw the background - a car can overlap up to 6 background squares. */
	    /* This could be optimized by not drawing all 6 every time. */
	
	    offs=((org_y/8)*32) + ((org_x/8)%32);
	
	    sx = 8 * (offs % 32)-org_x;
	    sy = 8 * (offs / 32)-org_y;
	
	    drawgfx(back_vid,Machine.gfx[0],
	            videoram.read(offs) & 0x3F, (videoram.read(offs) & 0x80)>>7,
				0,0,sx,sy, clip,TRANSPARENCY_NONE,0);
	
	    offs=((org_y/8)*32) + (((org_x+8)/8)%32);
	    sx = 8 * (offs % 32)-org_x;
	    sy = 8 * (offs / 32)-org_y;
	
	    drawgfx(back_vid,Machine.gfx[0],
	            videoram.read(offs) & 0x3F, (videoram.read(offs) & 0x80)>>7,
				0,0,sx,sy, clip,TRANSPARENCY_NONE,0);
	
	    offs=((org_y/8)*32) + (((org_x+16)/8)%32);
	    sx = 8 * (offs % 32)-org_x;
	    sy = 8 * (offs / 32)-org_y;
	
	    drawgfx(back_vid,Machine.gfx[0],
	            videoram.read(offs) & 0x3F, (videoram.read(offs) & 0x80)>>7,
				0,0,sx,sy, clip,TRANSPARENCY_NONE,0);
	
	    offs=(((org_y+8)/8)*32) + ((org_x/8)%32);
	    sx = 8 * (offs % 32)-org_x;
	    sy = 8 * (offs / 32)-org_y;
	
	    drawgfx(back_vid,Machine.gfx[0],
	            videoram.read(offs) & 0x3F, (videoram.read(offs) & 0x80)>>7,
				0,0,sx,sy, clip,TRANSPARENCY_NONE,0);
	
	    offs=(((org_y+8)/8)*32) + (((org_x+8)/8)%32);
	    sx = 8 * (offs % 32)-org_x;
	    sy = 8 * (offs / 32)-org_y;
	
	    drawgfx(back_vid,Machine.gfx[0],
	            videoram.read(offs) & 0x3F, (videoram.read(offs) & 0x80)>>7,
				0,0,sx,sy, clip,TRANSPARENCY_NONE,0);
	
	    offs=(((org_y+8)/8)*32) + (((org_x+16)/8)%32);
	    sx = 8 * (offs % 32)-org_x;
	    sy = 8 * (offs / 32)-org_y;
	
	    drawgfx(back_vid,Machine.gfx[0],
	            videoram.read(offs) & 0x3F, (videoram.read(offs) & 0x80)>>7,
				0,0,sx,sy, clip,TRANSPARENCY_NONE,0);
	
	
	
	
	    /* Grey car 1 */
	    sx=30*8-sprint2_horiz_ram.read(GREY_CAR1);
	    sy=31*8-sprint2_vert_car_ram.read(GREY_CAR1*2);
	    sx=sx-org_x;
	    sy=sy-org_y;
	
	    drawgfx(grey_cars_vid,Machine.gfx[1],
	            (sprint2_vert_car_ram.read(GREY_CAR1*2+1)>>3), GREY_CAR1,
	            0,0,sx,sy,clip,TRANSPARENCY_NONE,0);
	
	    /* Grey car 2 */
	    sx=30*8-sprint2_horiz_ram.read(GREY_CAR2);
	    sy=31*8-sprint2_vert_car_ram.read(GREY_CAR2*2);
	    sx=sx-org_x;
	    sy=sy-org_y;
	
	    drawgfx(grey_cars_vid,Machine.gfx[1],
	            (sprint2_vert_car_ram.read(GREY_CAR2*2+1)>>3), GREY_CAR2,
	            0,0,sx,sy,clip,TRANSPARENCY_COLOR,1);
	
	
	    /* White car */
	    sx=30*8-sprint2_horiz_ram.read(WHITE_CAR);
	    sy=31*8-sprint2_vert_car_ram.read(WHITE_CAR*2);
	    sx=sx-org_x;
	    sy=sy-org_y;
	
	    drawgfx(white_car_vid,Machine.gfx[1],
	            (sprint2_vert_car_ram.read(WHITE_CAR*2+1)>>3), WHITE_CAR,
	            0,0,sx,sy,clip,TRANSPARENCY_NONE,0);
	
	    /* Black car */
	    drawgfx(black_car_vid,Machine.gfx[1],
	            (sprint2_vert_car_ram.read(BLACK_CAR*2+1)>>3), BLACK_CAR,
	            0,0,0,0,clip,TRANSPARENCY_NONE,0);
	
	    /* Now check for Collision2 */
	    for (sy=0;sy<8;sy++)
	    {
	        for (sx=0;sx<16;sx++)
	        {
	                if (read_pixel.handler(black_car_vid, sx, sy)==Machine.pens.read(0))
	                {
						int back_pixel;
	
	                    /* Condition 1 - black car = white car */
	                    if (read_pixel.handler(white_car_vid, sx, sy)==Machine.pens.read(3))
	                        sprint2_collision2_data|=0x40;
	
	                    /* Condition 2 - black car = grey cars */
	                    if (read_pixel.handler(grey_cars_vid, sx, sy)==Machine.pens.read(2))
	                        sprint2_collision2_data|=0x40;
	
	                    back_pixel = read_pixel.handler(back_vid, sx, sy);
	
	                    /* Condition 3 - black car = black playfield (oil) */
	                    if (back_pixel==Machine.pens.read(0))
	                        sprint2_collision2_data|=0x40;
	
	                    /* Condition 4 - black car = white playfield (track) */
	                    if (back_pixel==Machine.pens.read(3))
	                        sprint2_collision2_data|=0x80;
	               }
	        }
	    }
	}
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr sprint_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs,car;
	
	    /* for every character in the Video RAM, check if it has been modified */
		/* since last time and update it accordingly. */
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				int charcode;
				int sx,sy;
	
				dirtybuffer[offs]=0;
	
				charcode = videoram.read(offs) & 0x3f;
	
				sx = 8 * (offs % 32);
				sy = 8 * (offs / 32);
				drawgfx(tmpbitmap,Machine.gfx[0],
						charcode, (videoram.read(offs) & 0x80)>>7,
						0,0,sx,sy,
						Machine.visible_area,TRANSPARENCY_NONE,0);
			}
		}
	
		/* copy the character mapped graphics */
		copybitmap(bitmap,tmpbitmap,0,0,0,0,Machine.visible_area,TRANSPARENCY_NONE,0);
	
		/* Draw each one of our four cars */
		for (car=3;car>=0;car--)
		{
			int sx,sy;
	
			sx=30*8-sprint2_horiz_ram.read(car);
			sy=31*8-sprint2_vert_car_ram.read(car*2);
	
			drawgfx(bitmap,Machine.gfx[1],
					(sprint2_vert_car_ram.read(car*2+1)>>3), car,
					0,0,sx,sy,
					Machine.visible_area,TRANSPARENCY_COLOR,1);
		}
	
		/* Refresh our collision detection buffers */
		sprint2_check_collision1(bitmap);
		sprint2_check_collision2(bitmap);
	} };
	
	
	static void draw_gear_indicator(int gear, mame_bitmap bitmap, int x, int color)
	{
		/* gear shift indicators - not a part of the original game!!! */
	
		char gear_buf[] = {0x07,0x05,0x01,0x12,0x00,0x00}; /* "GEAR  " */
		int offs;
	
		gear_buf[5] = (char) (0x30 + gear);
		for (offs = 0; offs < 6; offs++)
			drawgfx(bitmap,Machine.gfx[0],
					gear_buf[offs],color,
					0,0,(x+offs)*8,30*8,
					Machine.visible_area,TRANSPARENCY_NONE,0);
	}
	
	
	public static VhUpdatePtr sprint2_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		sprint_vh_screenrefresh.handler(bitmap,full_refresh);
	
		draw_gear_indicator(sprint2_gear1, bitmap, 25, 1);
		draw_gear_indicator(sprint2_gear2, bitmap, 1 , 0);
	} };
	
	public static VhUpdatePtr sprint1_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		sprint_vh_screenrefresh.handler(bitmap,full_refresh);
	
		draw_gear_indicator(sprint2_gear1, bitmap, 12, 1);
	} };
}
