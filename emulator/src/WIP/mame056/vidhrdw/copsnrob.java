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


public class copsnrob
{
	
	
	/*TODO*///static const struct artwork_element copsnrob_overlay[] =
	/*TODO*///{
	/*TODO*///	{{  0,  71, 0, 255}, 0x40, 0x40, 0xc0, OVERLAY_DEFAULT_OPACITY},	/* blue */
	/*TODO*///	{{ 72, 187, 0, 255}, 0xf0, 0xf0, 0x30, OVERLAY_DEFAULT_OPACITY},	/* yellow */
	/*TODO*///	{{188, 255, 0, 255}, 0xbd, 0x9b, 0x13, OVERLAY_DEFAULT_OPACITY},	/* amber */
	/*TODO*///	{{-1,-1,-1,-1},0,0,0,0}
	/*TODO*///};
	
	public static UBytePtr copsnrob_bulletsram = new UBytePtr();
	public static UBytePtr copsnrob_carimage = new UBytePtr();
	public static UBytePtr copsnrob_cary = new UBytePtr();
	public static UBytePtr copsnrob_trucky = new UBytePtr();
	public static UBytePtr copsnrob_truckram = new UBytePtr();
	
	
	public static VhStartPtr copsnrob_vh_start = new VhStartPtr() { public int handler() 
	{
		/*TODO*///overlay_create(copsnrob_overlay, 2);
	
	    return 0;
	} };
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr copsnrob_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		int offs, x, y;
	
	
	    /* redrawing the entire display is faster in this case */
	
	    for (offs = videoram_size[0];offs >= 0;offs--)
	    {
			int sx,sy;
	
			sx = 31 - (offs % 32);
			sy = offs / 32;
	
			drawgfx(bitmap,Machine.gfx[0],
					videoram.read(offs) & 0x3f,0,
					0,0,
					8*sx,8*sy,
					Machine.visible_area,TRANSPARENCY_NONE,0);
	    }
	
	
	    /* Draw the cars. Positioning was based on a screen shot */
	    if (copsnrob_cary.read(0) != 0)
	    {
	        drawgfx(bitmap,Machine.gfx[1],
	                copsnrob_carimage.read(0),0,
	                1,0,
	                0xe4,256-copsnrob_cary.read(0),
	                Machine.visible_area,TRANSPARENCY_PEN,0);
	    }
	
	    if (copsnrob_cary.read(1) != 0)
	    {
	        drawgfx(bitmap,Machine.gfx[1],
	                copsnrob_carimage.read(1),0,
	                1,0,
	                0xc4,256-copsnrob_cary.read(1),
	                Machine.visible_area,TRANSPARENCY_PEN,0);
	    }
	
	    if (copsnrob_cary.read(2) != 0)
	    {
	        drawgfx(bitmap,Machine.gfx[1],
	                copsnrob_carimage.read(2),0,
	                0,0,
	                0x24,256-copsnrob_cary.read(2),
	                Machine.visible_area,TRANSPARENCY_PEN,0);
	    }
	
	    if (copsnrob_cary.read(3) != 0)
	    {
	        drawgfx(bitmap,Machine.gfx[1],
	                copsnrob_carimage.read(3),0,
	                0,0,
	                0x04,256-copsnrob_cary.read(3),
	                Machine.visible_area,TRANSPARENCY_PEN,0);
	    }
	
	
	    /* Draw the beer truck. Positioning was based on a screen shot.
	        We scan the truck's window RAM for a location whose bit is set and
	        which corresponds either to the truck's front end or the truck's back
	        end (based on the value of the truck image line sync register). We
	        then draw a truck image in the proper place and continue scanning.
	        This is not a perfect emulation of the game hardware, but it should
	        suffice for the way the game software uses the hardware.  It does take
	        care of the problem of displaying multiple beer trucks and of scrolling
	        truck images smoothly off the top of the screen. */
	
	     for (y = 0; y < 256; y++)
	     {
			/* y is going up the screen, but the truck window RAM locations
			go down the screen. */
	
			if (copsnrob_truckram.read(255-y) != 0)
			{
				/* The hardware only uses the low 5 bits of the truck image line
				sync register. */
				if ((copsnrob_trucky.read(0) & 0x1f) == ((y+31) & 0x1f))
				{
					/* We've hit a truck's back end, so draw the truck.  The front
					   end may be off the top of the screen, but we don't care. */
					drawgfx(bitmap,Machine.gfx[2],
							0,0,
							0,0,
							0x80,256-(y+31),
							Machine.visible_area,TRANSPARENCY_PEN,0);
					/* Skip past this truck's front end so we don't draw this
					truck twice. */
					y += 31;
				}
				else if ((copsnrob_trucky.read(0) & 0x1f) == (y & 0x1f))
				{
					/* We missed a truck's back end (it was off the bottom of the
					   screen) but have hit its front end, so draw the truck. */
					drawgfx(bitmap,Machine.gfx[2],
							0,0,
							0,0,
							0x80,256-y,
							Machine.visible_area,TRANSPARENCY_PEN,0);
				}
			}
	    }
	
	
	    /* Draw the bullets.
	       They are flickered on/off every frame by the software, so don't
	       play it with frameskip 1 or 3, as they could become invisible */
	
	    for (x = 0; x < 256; x++)
	    {
		    int bullet, mask1, mask2, val;
	
	
	        val = copsnrob_bulletsram.read(x);
	
	        // Check for the most common case
	        if ((val & 0x0f)==0) continue;
	
	        mask1 = 0x01;
	        mask2 = 0x10;
	
	        // Check each bullet
	        for (bullet = 0; bullet < 4; bullet++)
	        {
	            if ((val & mask1) != 0)
	            {
	                for (y = 0; y <= Machine.visible_area.max_y; y++)
	                {
	                    if ((copsnrob_bulletsram.read(y) & mask2) != 0)
	                    {
	                        plot_pixel.handler(bitmap, 256-x, y, Machine.pens.read(1));
	                    }
	                }
	            }
	
	            mask1 <<= 1;
	            mask2 <<= 1;
	        }
	    }
	} };
}
