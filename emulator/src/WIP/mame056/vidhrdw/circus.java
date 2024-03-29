/***************************************************************************

  vidhrdw.c

  Functions to emulate the video hardware of the machine.

  CHANGES:
  MAB 05 MAR 99 - changed overlay support to use artwork functions

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

import static WIP2.mame056.palette.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.mame056.cpuintrf.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.sound.dac.*;
import static WIP2.mame056.sound.dacH.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP2.common.libc.cstring.*;

public class circus
{
	
	static int clown_x=0,clown_y=0,clown_z=0;
	
	/* The first entry defines the color with which the bitmap is filled initially */
	/* The array is terminated with an entry with negative coordinates. */
	/* At least two entries are needed. */
	/*TODO*///static artwork_element circus_ol[]={
	/*TODO*///	{{  0, 255,  20,  35}, 0x20, 0x20, 0xff,   OVERLAY_DEFAULT_OPACITY},	/* blue */
	/*TODO*///	{{  0, 255,  36,  47}, 0x20, 0xff, 0x20,   OVERLAY_DEFAULT_OPACITY},	/* green */
	/*TODO*///	{{  0, 255,  48,  63}, 0xff, 0xff, 0x20,   OVERLAY_DEFAULT_OPACITY},	/* yellow */
	/*TODO*///	{{ -1,  -1,  -1,  -1},    0,    0,    0,   0}
	/*TODO*///};
	
	
	/***************************************************************************
	***************************************************************************/
	
	public static VhStartPtr circus_vh_start = new VhStartPtr() { public int handler() 
	{
		int start_pen = 2;
	
		if (generic_vh_start.handler()!=0)
			return 1;
	
		/*TODO*///overlay_create(circus_ol, start_pen);
	
		return 0;
	} };
	
	/***************************************************************************
	***************************************************************************/
	
	public static WriteHandlerPtr circus_clown_x_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		clown_x = 240-data;
	} };
	
	public static WriteHandlerPtr circus_clown_y_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		clown_y = 240-data;
	} };
	
	/* This register controls the clown image currently displayed */
	/* and also is used to enable the amplifier and trigger the   */
	/* discrete circuitry that produces sound effects and music   */
	
	public static WriteHandlerPtr circus_clown_z_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		clown_z = (data & 0x0f);
	
		/* Bits 4-6 enable/disable trigger different events */
		/* descriptions are based on Circus schematics      */
	
		switch ((data & 0x70) >> 4)
		{
			case 0 : /* All Off */
				DAC_data_w (0,0);
				break;
	
			case 1 : /* Music */
				DAC_data_w (0,0x7f);
				break;
	
			case 2 : /* Pop */
				break;
	
			case 3 : /* Normal Video */
				break;
	
			case 4 : /* Miss */
				break;
	
			case 5 : /* Invert Video */
				break;
	
			case 6 : /* Bounce */
				break;
	
			case 7 : /* Don't Know */
				break;
		};
	
		/* Bit 7 enables amplifier (1 = on) */
	
	//	logerror("clown Z = %02x\n",data);
	} };
	
	static void draw_line(mame_bitmap bitmap, int x1, int y1, int x2, int y2, int dotted)
	{
		/* Draws horizontal and Vertical lines only! */
	    int col = Machine.pens.read(1);
	
	    int count, skip;
	
		/* Draw the Line */
	
		if (dotted > 0)
			skip = 2;
		else
			skip = 1;
	
		if (x1 == x2)
		{
			for (count = y2; count >= y1; count -= skip)
			{
				plot_pixel.handler(bitmap, x1, count, col);
			}
		}
		else
		{
			for (count = x2; count >= x1; count -= skip)
			{
				plot_pixel.handler(bitmap, count, y1, col);
			}
		}
	}
	
	static void draw_robot_box (mame_bitmap bitmap, int x, int y)
	{
		/* Box */
	
		int ex = x + 24;
		int ey = y + 26;
	
		draw_line(bitmap,x,y,ex,y,0);		/* Top */
		draw_line(bitmap,x,ey,ex,ey,0);		/* Bottom */
		draw_line(bitmap,x,y,x,ey,0);		/* Left */
		draw_line(bitmap,ex,y,ex,ey,0);		/* Right */
	
		/* Score Grid */
	
		ey = y + 10;
		draw_line(bitmap,x+8,ey,ex,ey,0);	/* Horizontal Divide Line */
		draw_line(bitmap,x+8,y,x+8,ey,0);
		draw_line(bitmap,x+16,y,x+16,ey,0);
	}
	
	
	public static VhUpdatePtr circus_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
		int sx,sy;
	
		if (full_refresh != 0)
		{
			memset(dirtybuffer,1,videoram_size[0]);
		}
	
		/* for every character in the Video RAM,        */
		/* check if it has been modified since          */
		/* last time and update it accordingly.         */
	
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				dirtybuffer[offs] = 0;
	
				sy = offs / 32;
				sx = offs % 32;
	
				drawgfx(bitmap,Machine.gfx[0],
						videoram.read(offs),
						0,
						0,0,
						8*sx,8*sy,
						Machine.visible_area,TRANSPARENCY_NONE,0);
			}
		}
	
	    /* The sync generator hardware is used to   */
	    /* draw the border and diving boards        */
	
	    draw_line (bitmap,0,18,255,18,0);
	    draw_line (bitmap,0,249,255,249,1);
	    draw_line (bitmap,0,18,0,248,0);
	    draw_line (bitmap,247,18,247,248,0);
	
	    draw_line (bitmap,0,137,17,137,0);
	    draw_line (bitmap,231,137,248,137,0);
	    draw_line (bitmap,0,193,17,193,0);
	    draw_line (bitmap,231,193,248,193,0);
	
		drawgfx(bitmap,Machine.gfx[1],
				clown_z,
				0,
				0,0,
				clown_y,clown_x,
				Machine.visible_area,TRANSPARENCY_PEN,0);
	
		/* mark tiles underneath as dirty */
		sx = clown_y >> 3;
		sy = clown_x >> 3;
	
		{
			int max_x = 2;
			int max_y = 2;
			int x2, y2;
	
			if ((clown_y & 0x0f)!=0) max_x ++;
			if ((clown_x & 0x0f)!=0) max_y ++;
	
			for (y2 = sy; y2 < sy + max_y; y2 ++)
			{
				for (x2 = sx; x2 < sx + max_x; x2 ++)
				{
					if ((x2 < 32) && (y2 < 32) && (x2 >= 0) && (y2 >= 0))
						dirtybuffer[x2 + 32*y2] = 1;
				}
			}
		}
	} };
	
	
	public static VhUpdatePtr robotbowl_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
		int sx,sy;
	
		if (full_refresh != 0)
		{
			memset(dirtybuffer, 1, videoram_size[0]);
		}
	
		/* for every character in the Video RAM,  */
		/* check if it has been modified since    */
		/* last time and update it accordingly.   */
	
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				dirtybuffer[offs] = 0;
	
				sx = offs % 32;
				sy = offs / 32;
	
				drawgfx(bitmap,Machine.gfx[0],
						videoram.read(offs),
						0,
						0,0,
						8*sx,8*sy,
						Machine.visible_area,TRANSPARENCY_NONE,0);
			}
		}
	
	    /* The sync generator hardware is used to   */
	    /* draw the bowling alley & scorecards      */
	
	    /* Scoreboards */
	
	    for(offs=15;offs<=63;offs+=24)
	    {
	        draw_robot_box(bitmap, offs, 31);
	        draw_robot_box(bitmap, offs, 63);
	        draw_robot_box(bitmap, offs, 95);
	
	        draw_robot_box(bitmap, offs+152, 31);
	        draw_robot_box(bitmap, offs+152, 63);
	        draw_robot_box(bitmap, offs+152, 95);
	    }
	
	    draw_robot_box(bitmap, 39, 127);                  /* 10th Frame */
	    draw_line(bitmap, 39,137,47,137,0);          /* Extra digit box */
	
	    draw_robot_box(bitmap, 39+152, 127);
	    draw_line(bitmap, 39+152,137,47+152,137,0);
	
	    /* Bowling Alley */
	
	    draw_line(bitmap, 103,17,103,205,0);
	    draw_line(bitmap, 111,17,111,203,1);
	    draw_line(bitmap, 152,17,152,205,0);
	    draw_line(bitmap, 144,17,144,203,1);
	
		/* Draw the Ball */
	
		drawgfx(bitmap,Machine.gfx[1],
				clown_z,
				0,
				0,0,
				clown_y+8,clown_x+8, /* Y is horizontal position */
				Machine.visible_area,TRANSPARENCY_PEN,0);
	
		/* mark tiles underneath as dirty */
		sx = clown_y >> 3;
		sy = clown_x >> 3;
	
		{
			int max_x = 2;
			int max_y = 2;
			int x2, y2;
	
			if ((clown_y & 0x0f)!=0) max_x ++;
			if ((clown_x & 0x0f)!=0) max_y ++;
	
			for (y2 = sy; y2 < sy + max_y; y2 ++)
			{
				for (x2 = sx; x2 < sx + max_x; x2 ++)
				{
					if ((x2 < 32) && (y2 < 32) && (x2 >= 0) && (y2 >= 0))
						dirtybuffer[x2 + 32*y2] = 1;
				}
			}
		}
	
	} };
	
	public static VhUpdatePtr crash_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs;
		int sx,sy;
	
		if (full_refresh != 0)
		{
			memset(dirtybuffer, 1, videoram_size[0]);
		}
	
		/* for every character in the Video RAM,	*/
		/* check if it has been modified since 		*/
		/* last time and update it accordingly. 	*/
	
		for (offs = videoram_size[0] - 1;offs >= 0;offs--)
		{
			if (dirtybuffer[offs] != 0)
			{
				dirtybuffer[offs] = 0;
	
				sx = offs % 32;
				sy = offs / 32;
	
				drawgfx(bitmap,Machine.gfx[0],
						videoram.read(offs),
						0,
						0,0,
						8*sx,8*sy,
						Machine.visible_area,TRANSPARENCY_NONE,0);
			}
		}
	
		/* Draw the Car */
	    drawgfx(bitmap,Machine.gfx[1],
				clown_z,
				0,
				0,0,
				clown_y,clown_x, /* Y is horizontal position */
				Machine.visible_area,TRANSPARENCY_PEN,0);
	
		/* mark tiles underneath as dirty */
		sx = clown_y >> 3;
		sy = clown_x >> 3;
	
		{
			int max_x = 2;
			int max_y = 2;
			int x2, y2;
	
			if ((clown_y & 0x0f)!=0) max_x ++;
			if ((clown_x & 0x0f)!=0) max_y ++;
	
			for (y2 = sy; y2 < sy + max_y; y2 ++)
			{
				for (x2 = sx; x2 < sx + max_x; x2 ++)
				{
					if ((x2 < 32) && (y2 < 32) && (x2 >= 0) && (y2 >= 0))
						dirtybuffer[x2 + 32*y2] = 1;
				}
			}
		}
	} };
}
