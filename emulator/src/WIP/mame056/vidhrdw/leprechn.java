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
import static WIP2.common.libc.cstring.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.cpuintrf.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.mame056.palette.*;
// refactor
import static WIP2.arcadeflex036.osdepend.logerror;

public class leprechn
{
	
	
	static int x,y,screen_width;
	static int last_command;
	
	// We reason we need this pending business, is that otherwise, when the guy
	// walks on the rainbow, he'd leave a trail behind him
	static int pending, pending_x, pending_y, pending_color;
	
	public static WriteHandlerPtr leprechn_graphics_command_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
	    last_command = data;
	} };
	
	public static WriteHandlerPtr leprechn_graphics_data_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
	    int direction;
	
	    if (pending != 0)
	    {
			plot_pixel.handler(Machine.scrbitmap, pending_x, pending_y, Machine.pens.read(pending_color));
	        videoram.write(pending_y * screen_width + pending_x,pending_color);
	
	        pending = 0;
	    }
	
	    switch (last_command)
	    {
	    // Write Command
	    case 0x00:
	        direction = (data & 0xf0) >> 4;
	        switch (direction)
	        {
	        case 0x00:
	        case 0x04:
	        case 0x08:
	        case 0x0c:
	            break;
	
	        case 0x01:
	        case 0x09:
	            x++;
	            break;
	
	        case 0x02:
	        case 0x06:
	            y++;
	            break;
	
	        case 0x03:
	            x++;
	            y++;
	            break;
	
	        case 0x05:
	        case 0x0d:
	            x--;
	            break;
	
	        case 0x07:
	            x--;
	            y++;
	            break;
	
	        case 0x0a:
	        case 0x0e:
	            y--;
	            break;
	
	        case 0x0b:
	            x++;
	            y--;
	            break;
	
	        case 0x0f:
	            x--;
	            y--;
	            break;
	        }
	
	        x = x & 0xff;
	        y = y & 0xff;
	
	        pending = 1;
	        pending_x = x;
	        pending_y = y;
	        pending_color = data & 0x0f;
	
	        return;
	
	    // X Position Write
	    case 0x08:
	        x = data;
	        return;
	
	    // Y Position Write
	    case 0x10:
	        y = data;
	        return;
	
	    // Clear Bitmap
	    case 0x18:
	        fillbitmap(Machine.scrbitmap,Machine.pens.read(data),null);
	        memset(videoram, data, screen_width * Machine.drv.screen_height);
	        return;
	    }
	
	    // Just a precaution. Doesn't seem to happen.
	    logerror("Unknown Graphics Command #%2X at %04X\n", last_command, cpu_get_pc());
	} };
	
	
	public static ReadHandlerPtr leprechn_graphics_data_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
	    return videoram.read(y * screen_width + x);
	} };
	
	
	/***************************************************************************
	
	  Start the video hardware emulation.
	
	***************************************************************************/
	public static VhStartPtr leprechn_vh_start = new VhStartPtr() { public int handler() 
	{
	    screen_width = Machine.drv.screen_width;
	
	    if ((videoram = new UBytePtr(screen_width*Machine.drv.screen_height)) == null)
	    {
	        return 1;
	    }
	
	    pending = 0;
	
	    return 0;
	} };
	
	/***************************************************************************
	
	  Stop the video hardware emulation.
	
	***************************************************************************/
	public static VhStopPtr leprechn_vh_stop = new VhStopPtr() { public void handler() 
	{
	    videoram = null;
	} };
	
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr leprechn_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		if (full_refresh != 0)
		{
			int sx, sy;
	
			/* redraw bitmap */
	
			for (sx = 0; sx < screen_width; sx++)
			{
				for (sy = 0; sy < Machine.drv.screen_height; sy++)
				{
					plot_pixel.handler(Machine.scrbitmap, sx, sy, Machine.pens.read(videoram.read(sy * screen_width + sx)));
				}
			}
		}
	} };
}
