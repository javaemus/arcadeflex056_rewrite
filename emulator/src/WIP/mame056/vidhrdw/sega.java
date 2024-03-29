/***************************************************************************

  vidhrdw.c

  Generic functions used by the Sega Vector games

***************************************************************************/

/*
 * History:
 *
 * 97???? Converted Al Kossow's G80 sources. LBO
 * 970807 Scaling support and dynamic sin/cos tables. ASG
 * 980124 Suport for antialiasing. .ac
 * 980203 cleaned up and interfaced to generic vector routines. BW
 *
 * TODO: use floating point math instead of fixed point.
 */

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.vidhrdw;

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

import static WIP.mame056.vidhrdw.vector.*;
import static WIP.mame056.vidhrdw.vectorH.*;

public class sega
{
	
	public static int VEC_SHIFT = 15;	/* do not use a higher value. Values will overflow */
	
	static int width, height, cent_x, cent_y, min_x, min_y, max_x, max_y;
	static long[] sinTable, cosTable;
	static int intensity;
	
	public static void sega_generate_vector_list ()
	{
		int deltax, deltay;
		int currentX, currentY;
	
		int vectorIndex;
		int symbolIndex;
	
		int rotate, scale;
		int attrib;
	
		int angle, length;
		int color;
	
		int draw;
	
		vector_clear_list();
	
		symbolIndex = 0;	/* Reset vector PC to 0 */
	
		/*
		 * walk the symbol list until 'last symbol' set
		 */
	
		do {
			draw = vectorram.read(symbolIndex++);
	
			if ((draw & 1) != 0)	/* if symbol active */
			{
				currentX    = vectorram.read(symbolIndex + 0) | (vectorram.read(symbolIndex + 1) << 8);
				currentY    = vectorram.read(symbolIndex + 2) | (vectorram.read(symbolIndex + 3) << 8);
				vectorIndex = vectorram.read(symbolIndex + 4) | (vectorram.read(symbolIndex + 5) << 8);
				rotate      = vectorram.read(symbolIndex + 6) | (vectorram.read(symbolIndex + 7) << 8);
				scale       = vectorram.read(symbolIndex + 8);
	
				currentX = ((currentX & 0x7ff) - min_x) << VEC_SHIFT;
				currentY = (max_y - (currentY & 0x7ff)) << VEC_SHIFT;
				vector_add_point ( currentX, currentY, 0, 0);
				vectorIndex &= 0xfff;
	
				/* walk the vector list until 'last vector' bit */
				/* is set in attributes */
	
				do
				{
					attrib = vectorram.read(vectorIndex + 0);
					length = vectorram.read(vectorIndex + 1);
					angle  = vectorram.read(vectorIndex + 2) | (vectorram.read(vectorIndex + 3) << 8);
	
					vectorIndex += 4;
	
					/* calculate deltas based on len, angle(s), and scale factor */
	
					angle = (angle + rotate) & 0x3ff;
					deltax = (int) (sinTable[angle] * scale * length);
					deltay = (int) (cosTable[angle] * scale * length);
	
					currentX += deltax >> 7;
					currentY -= deltay >> 7;
	
					color = VECTOR_COLOR222((attrib >> 1) & 0x3f);
					if (!(((attrib & 1) != 0) && color!=0))
					{
						if (translucency != 0)
							intensity = 0xa0; /* leave room for translucency */
						else
							intensity = 0xff;
					}
					else
						intensity = 0;
					vector_add_point ( currentX, currentY, color, intensity );
	
				} while ((attrib & 0x80)==0);
			}
	
			symbolIndex += 9;
			if (symbolIndex >= vectorram_size[0])
				break;
	
		} while ((draw & 0x80)==0);
	}
	
	/***************************************************************************
	
	  Start the video hardware emulation.
	
	***************************************************************************/
	public static VhStartPtr sega_vh_start = new VhStartPtr() { public int handler() 
	{
		int i;
	
		if (vectorram_size[0] == 0)
			return 1;
		min_x =Machine.visible_area.min_x;
		min_y =Machine.visible_area.min_y;
		max_x =Machine.visible_area.max_x;
		max_y =Machine.visible_area.max_y;
		width =max_x-min_x;
		height=max_y-min_y;
		cent_x=(max_x+min_x)/2;
		cent_y=(max_y+min_y)/2;
	
		vector_set_shift (VEC_SHIFT);
	
		/* allocate memory for the sine and cosine lookup tables ASG 080697 */
		sinTable = new long[0x400];
		if (sinTable == null)
			return 1;
		cosTable = new long[0x400];
		if (cosTable == null)
		{
			sinTable = null;
			return 1;
		}
	
		/* generate the sine/cosine lookup tables */
		for (i = 0; i < 0x400; i++)
		{
			double angle = ((2. * Math.PI) / (double)0x400) * (double)i;
			double temp;
	
			temp = Math.sin (angle);
			if (temp < 0)
				sinTable[i] = (long)(temp * (double)(1 << VEC_SHIFT) - 0.5);
			else
				sinTable[i] = (long)(temp * (double)(1 << VEC_SHIFT) + 0.5);
	
			temp = Math.cos (angle);
			if (temp < 0)
				cosTable[i] = (long)(temp * (double)(1 << VEC_SHIFT) - 0.5);
			else
				cosTable[i] = (long)(temp * (double)(1 << VEC_SHIFT) + 0.5);
	
		}
	
		return vector_vh_start.handler();
	} };
	
	/***************************************************************************
	
	  Stop the video hardware emulation.
	
	***************************************************************************/
	public static VhStopPtr sega_vh_stop = new VhStopPtr() { public void handler() 
	{
		if (sinTable != null)
			
		sinTable = null;
                
		if (cosTable != null)
			
		cosTable = null;
	
		vector_vh_stop.handler();
	} };
	
	/***************************************************************************
	
	  Draw the game screen in the given mame_bitmap.
	  Do NOT call osd_update_display() from this function, it will be called by
	  the main emulation engine.
	
	***************************************************************************/
	public static VhUpdatePtr sega_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		sega_generate_vector_list();
		vector_vh_screenrefresh.handler(bitmap,full_refresh);
	} };
}
