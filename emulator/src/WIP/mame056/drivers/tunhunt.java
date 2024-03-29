/*	tunhunt.c
 *
 *	MAME driver for Tunnel Hunt (C)1981
 *		Developed by Atari
 *		Hardware by Dave Sherman
 *		Game Programmed by Owen Rubin
 *		Licensed and Distributed by Centuri
 *
 *	Many thanks to Owen Rubin for invaluable hardware information and
 *	game description!
 *
 *	Known Issues:
 *
 *	Coin Input seems unresponsive.
 *
 *	Colors:
 *	- Hues are hardcoded.  There doesn't appear to be any logical way to
 *		map the color proms so that the correct colors appear.
 *		See last page of schematics for details.  Are color proms bad?
 *
 *	Alphanumeric Layer:
 *	- placement for some characters seems strange (but may well be correct).
 *
 *	Shell Objects:
 *	- vstretch/placement/color handling isn't confirmed
 *	- two bitplanes per character or two banks?
 *
 *	Motion Object:
 *	- enemy ships look funny when they get close (to ram player)
 *	- stretch probably isn't implemented correctly (see splash screen
 *		with zooming "TUNNEL HUNT" logo.
 *	- colors may not be mapped correctly.
 *
 *	Square Generator:
 *	- needs optimization
 */

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;
import static mame056.palette.*;
import static WIP2.common.ptr.*;
import static WIP2.common.libc.cstring.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.drawgfx.*;
import static WIP2.mame056.inputH.*;
import static WIP2.mame056.sndintrfH.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sound.samples.*;
import static WIP2.mame056.sound.samplesH.*;
import static WIP2.mame056.sound.mixer.*;
import static WIP2.mame056.sound.mixerH.*;
import static WIP2.mame056.sound.dac.*;
import static WIP2.mame056.sound.dacH.*;

import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.mame056.timer.*;
import static WIP2.mame056.timerH.*;

import static WIP2.arcadeflex056.fileio.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.sound.pokey.*;
import static WIP2.mame056.sound.pokeyH.*;

import static WIP2.mame056.vidhrdw.generic.*;

public class tunhunt
{
	
	/****************************************************************************************/
	
	/* Video Hardware Addresses */
	
	/* Square Generator (13 bytes each) */
	public static int LINEV         = 0x1403;	// LINES VERTICAL START
	public static int LINEVS	= 0x1483;	// LINES VERT STOP
	public static int LINEH         = 0x1083;	// LINES HORIZ START
	public static int LINEC         = 0x1283;	// LINE COLOR, 4 BITS D0-D3
	public static int LINESH	= 0x1203;	// LINES SLOPE 4 BITS D0-D3 (signed)
	/* LINESH was used for rotation effects in an older version of the game */
	
	/* Shell Object0 */
	public static int SHEL0H	= 0x1800;	// SHELL H POSITON (NORMAL SCREEN)
	public static int SHL0V         = 0x1400;	// SHELL V START(NORMAL SCREEN)
	public static int SHL0VS	= 0x1480;	// SHELL V STOP (NORMAL SCREEN)
	public static int SHL0ST	= 0x1200;	// SHELL VSTRETCH (LIKE MST OBJ STRECTH)
	public static int SHL0PC	= 0x1280;	// SHELL PICTURE CODE (D3-D0)
	
	/* Shell Object1 (see above) */
	public static int SHEL1H	= 0x1A00;
	public static int SHL1V         = 0x1401;
	public static int SHL1VS	= 0x1481;
	public static int SHL1ST	= 0x1201;
	public static int SHL1PC	= 0x1281;
	
	/* Motion Object RAM */
	public static int MOBJV         = 0x1C00;	// V POSITION (SCREEN ON SIDE)
	public static int MOBVS         = 0x1482;	// V STOP OF MOTION OBJECT (NORMAL SCREEN)
	public static int MOBJH         = 0x1402;	// H POSITON (SCREEN ON SIDE) (VSTART - NORMAL SCREEN)
	public static int MOBST         = 0x1082;	// STARTING LINE FOR RAM SCAN ON MOBJ
	public static int VSTRLO	= 0x1202;	// VERT (SCREEN ON SIDE) STRETCH MOJ OBJ
	public static int MOTT          = 0x2C00;	// MOTION OBJECT RAM (00-0F NOT USED, BYT CLEARED)
	public static int MOBSC0	= 0x1080;	// SCAN ROM START FOR MOBJ (unused?)
	public static int MOBSC1	= 0x1081;	// (unused?)
	
	/****************************************************************************************/
	
	static int tunhunt_control;
	
	public static WriteHandlerPtr tunhunt_control_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		/*
			0x01	coin counter#2	"right counter"
			0x02	coin counter#1	"center counter"
			0x04	"left counter"
			0x08	cover screen (shell0 hstretch)
			0x10	cover screen (shell1 hstretch)
			0x40	start LED
			0x80	in-game
		*/
		tunhunt_control = data;
		coin_counter_w.handler(0,data&0x01 );
		coin_counter_w.handler(1,data&0x02 );
		/*TODO*///set_led_status( 0, data&0x40 ); /* start */
	} };
	
	public static WriteHandlerPtr tunhunt_mott_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if( spriteram.read(offset)!=data )
		{
			spriteram.write(offset,data);
			dirtybuffer[offset>>4] = 1;
		}
	} };
	
	public static VhStartPtr tunhunt_vh_start = new VhStartPtr() { public int handler() 
	{
		/*
		Motion Object RAM contains 64 lines of run-length encoded data.
		We keep track of dirty lines and cache the expanded bitmap.
		With max RLE expansion, bitmap size is 256x64.
		*/
		dirtybuffer = new char[64];
		if( dirtybuffer != null )
		{
			memset( dirtybuffer, 1, 64 );
			tmpbitmap = bitmap_alloc( 256, 64 );
			if( tmpbitmap != null ) return 0;
		}
		dirtybuffer = null;
		return -1;
	} };
	
	public static VhStopPtr tunhunt_vh_stop = new VhStopPtr() { public void handler() 
	{
                dirtybuffer = null;
		bitmap_free(tmpbitmap);
	} };
	
	public static VhConvertColorPromPtr tunhunt_vh_convert_color_prom = new VhConvertColorPromPtr() {
            public void handler(char[] palette, char[] colortable, UBytePtr color_prom) {
                /* Tunnel Hunt uses a combination of color proms and palette RAM to specify a 16 color
		 * palette.  Here, we manage only the mappings for alphanumeric characters and SHELL
		 * graphics, which are unpacked ahead of time and drawn using MAME's drawgfx primitives.
		 */
	
		/* AlphaNumerics (1bpp)
		 *	2 bits of hilite select from 4 different background colors
		 *	Foreground color is always pen#4
		 *	Background color is mapped as follows:
		 */
	
		/* alpha hilite#0 */
		colortable[0] = 0x0; /* background color#0 (transparent) */
		colortable[1] = 0x4; /* foreground color */
	
		/* alpha hilite#1 */
		colortable[2] = 0x5; /* background color#1 */
		colortable[3] = 0x4; /* foreground color */
	
		/* alpha hilite#2 */
		colortable[4] = 0x6; /* background color#2 */
		colortable[5] = 0x4; /* foreground color */
	
		/* alpha hilite#3 */
		colortable[6] = 0xf; /* background color#3 */
		colortable[7] = 0x4; /* foreground color */
	
		/* shell graphics; these are either 1bpp (2 banks) or 2bpp.  It isn't clear which.
		 * In any event, the following pens are associated with the shell graphics:
		 */
		colortable[0x8] = 0;
		colortable[0x9] = 4;//1;
		colortable[0xa] = 2;
		colortable[0xb] = 4;
            }
        };
	
	/*
	Color Array Ram Assignments:
	    Location
	        0               Blanking, border
	        1               Mot Obj (10) (D), Shell (01)
	        2               Mot Obj (01) (G), Shell (10)
	        3               Mot Obj (00) (W)
	        4               Alpha & Shell (11) - shields
	        5               Hilight 1
	        6               Hilight 2
	        8-E             Lines (as normal) background
	        F               Hilight 3
	*/
        
        public static int APPLY_SHADE( int C, int S ){
            return ((C*S)/0xf);
        }
        
	static void update_palette()
	{
	//	const unsigned char *color_prom = memory_region( REGION_PROMS );
	/*
		The actual contents of the color proms (unused by this driver)
		are as follows:
	
		D11 "blue/green"
		0000:	00 00 8b 0b fb 0f ff 0b
				00 00 0f 0f fb f0 f0 ff
	
		C11 "red"
		0020:	00 f0 f0 f0 b0 b0 00 f0
				00 f0 f0 00 b0 00 f0 f0
	*/
		int color;
		int shade;
		int i;
		int red,green,blue;
	
		for( i=0; i<16; i++ )
		{
			color = paletteram.read(i);
			shade = 0xf^(color>>4);
	
			color &= 0xf; /* hue select */
			switch( color )
			{
			default:
			case 0x0: red = 0xff; green = 0xff; blue = 0xff; break; /* white */
			case 0x1: red = 0xff; green = 0x00; blue = 0xff; break; /* purple */
			case 0x2: red = 0x00; green = 0x00; blue = 0xff; break; /* blue */
			case 0x3: red = 0x00; green = 0xff; blue = 0xff; break; /* cyan */
			case 0x4: red = 0x00; green = 0xff; blue = 0x00; break; /* green */
			case 0x5: red = 0xff; green = 0xff; blue = 0x00; break; /* yellow */
			case 0x6: red = 0xff; green = 0x00; blue = 0x00; break; /* red */
			case 0x7: red = 0x00; green = 0x00; blue = 0x00; break; /* black? */
	
			case 0x8: red = 0xff; green = 0x7f; blue = 0x00; break; /* orange */
			case 0x9: red = 0x7f; green = 0xff; blue = 0x00; break; /* ? */
			case 0xa: red = 0x00; green = 0xff; blue = 0x7f; break; /* ? */
			case 0xb: red = 0x00; green = 0x7f; blue = 0xff; break; /* ? */
			case 0xc: red = 0xff; green = 0x00; blue = 0x7f; break; /* ? */
			case 0xd: red = 0x7f; green = 0x00; blue = 0xff; break; /* ? */
			case 0xe: red = 0xff; green = 0xaa; blue = 0xaa; break; /* ? */
			case 0xf: red = 0xaa; green = 0xaa; blue = 0xff; break; /* ? */
			}
	
		/* combine color components with shade value (0..0xf) */
			
			red	= APPLY_SHADE(red,shade);
			green	= APPLY_SHADE(green,shade);
			blue	= APPLY_SHADE(blue,shade);
	
			palette_set_color( i,red,green,blue );
		}
	}
	
	static GfxLayout alpha_layout = new GfxLayout
	(
		8,8,
		0x40,
		1,
		new int[] { 4 }, /* plane offsets */
		new int[] { 0,1,2,3,8,9,10,11 }, /* x offsets */
		new int[] {
			0x00,0x10,0x20,0x30,0x40,0x50,0x60,0x70
		}, /* y offsets */
		0x80
	);
	
	static GfxLayout obj_layout = new GfxLayout
	(
		16,16,
		8, /* number of objects */
		1, /* number of bitplanes */
		new int[] { 4 }, /* plane offsets */
		new int[] {
			0x00+0,0x00+1,0x00+2,0x00+3,
			0x08+0,0x08+1,0x08+2,0x08+3,
			0x10+0,0x10+1,0x10+2,0x10+3,
			0x18+0,0x18+1,0x18+2,0x18+3
		 }, /* x offsets */
		new int[] {
			0x0*0x20, 0x1*0x20, 0x2*0x20, 0x3*0x20,
			0x4*0x20, 0x5*0x20, 0x6*0x20, 0x7*0x20,
			0x8*0x20, 0x9*0x20, 0xa*0x20, 0xb*0x20,
			0xc*0x20, 0xd*0x20, 0xe*0x20, 0xf*0x20
		}, /* y offsets */
		0x200
	);
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0x000, alpha_layout, 0, 4 ),
		new GfxDecodeInfo( REGION_GFX2, 0x200, obj_layout,	 8, 1 ),
		new GfxDecodeInfo( REGION_GFX2, 0x000, obj_layout,	 8, 1 ), /* second bank, or second bitplane? */
		new GfxDecodeInfo( -1 )
	};
	
	static void draw_text( mame_bitmap bitmap )
	{
		/* 8 columns, 32 rows */
		int offs;
		int sx,sy,tile,color;
		UBytePtr source = new UBytePtr(videoram);
		for( offs=0; offs<0x100; offs++ )
		{
			sx = (offs/32)*8 + 256 - 64;
			sy = (offs%32)*8;
			tile = source.read(offs);
			color = tile>>6;
	
			drawgfx( bitmap, Machine.gfx[0],
				tile&0x3f,	/* lower 6 bits of character code */
				color,		/* upper 2 bits of character code */
				0,0,		/* flipx, flipy */
				sx,sy,		/* character placement */
				Machine.visible_area, /* clip */
				/* if hilite = 0, make the character background transparent */
				(color==0)?TRANSPARENCY_PEN:TRANSPARENCY_NONE,
				0 );
		}
	}
	
	static void draw_motion_object( mame_bitmap bitmap )
	{
	/*
	 *		VSTRLO	0x1202
	 *			normally 0x02 (gameplay, attract1)
	 *			in attract2 (with "Tunnel Hunt" graphic), decrements from 0x2f down to 0x01
	 *			goes to 0x01 for some enemy shots
	 *
	 *		MOBSC0	0x1080
	 *		MOBSC1	0x1081
	 *			always 0x00?
	 */
		UBytePtr pMem = new UBytePtr(memory_region( REGION_CPU1 ));
	//	int skip = pMem[MOBST];
		int x0 = 255-pMem.read(MOBJV);
		int y0 = 255-pMem.read(MOBJH);
		int scalex,scaley;
		int line,span;
		int x,span_data;
		int color;
		int count,pen;
		UBytePtr source = new UBytePtr();
		rectangle clip = new rectangle(Machine.visible_area);
	
		for( line=0; line<64; line++ )
		{
			if( dirtybuffer[line] != 0 )
			{
				dirtybuffer[line] = 0;
				x = 0;
				source = new UBytePtr(spriteram,line*0x10);
				for( span=0; span<0x10; span++ )
				{
					span_data = source.read(span);
					if( span_data == 0xff ) break;
					pen = ((span_data>>6)&0x3)^0x3;
					color = Machine.pens.read(pen);
					count = (span_data&0x1f)+1;
					while( count-- != 0)
					{
						plot_pixel.handler( tmpbitmap, x++,line,color );
					}
				}
				color = Machine.pens.read(0);
				while( x<256 )
				{
					plot_pixel.handler(tmpbitmap, x++,line,color );
				}
			} /* dirty line */
		} /* next line */
	
		switch( pMem.read(VSTRLO) )
		{
		case 0x01:
			scaley = (int) ((1<<16)*0.33); /* seems correct */
			break;
	
		case 0x02:
			scaley = (int) ((1<<16)*0.50); /* seems correct */
			break;
	
		default:
			scaley = (1<<16)*pMem.read(VSTRLO)/4; /* ??? */
			break;
		}
		scalex = (1<<16);
	
		copyrozbitmap(
			bitmap,tmpbitmap,
			-x0*scalex,/* startx */
			-y0*scaley,/* starty */
			scalex,/* incxx */
			0,0,/* incxy,incyx */
			scaley,/* incyy */
			0, /* no wraparound */
			clip,
			TRANSPARENCY_PEN,Machine.pens.read(0),
			0 /* priority */
		);
	}
	
	static void draw_box( mame_bitmap bitmap )
	{
	/*
		This is unnecessarily slow, but the box priorities aren't completely understood,
		yet.  Once understood, this function should be converted to use fillbitmap with
		rectangular chunks instead of plot_pixel.
	
		Tunnels:
			1080: 00 00 00 		01	e7 18   ae 51   94 6b   88 77   83 7c   80 7f  	x0
			1480: 00 f0 17 		00	22 22   5b 5b   75 75   81 81   86 86   89 89  	y0
			1400: 00 00 97 		ff	f1 f1   b8 b8   9e 9e   92 92   8d 8d   8a 8a  	y1
			1280: 07 03 00 		07	07 0c   0c 0d   0d 0e   0e 08   08 09   09 0a  	palette select
	
		Color Bars:
			1080: 00 00 00 		01	00 20 40 60 80 a0 c0 e0		01 2a	50 7a		x0
			1480: 00 f0 00 		00	40 40 40 40 40 40 40 40		00 00	00 00		y0
			1400: 00 00 00 		ff	ff ff ff ff ff ff ff ff		40 40	40 40		y1
			1280: 07 03 00 		01	07 06 04 05 02 07 03 00		09 0a	0b 0c		palette select
			.hue 06 02 ff 		60	06 05 03 04 01 06 02 ff		d2 00	c2 ff
	*/
		int span,x,y;
		UBytePtr pMem = new UBytePtr();
		int color;
		int pen;
	//	struct rectangle bbox;
		int z;
		int x0,y0,y1;
		pMem = memory_region( REGION_CPU1 );
	
		for( y=0; y<256; y++ )
		{
			for( x=0; x<256; x++ )
			{
				pen = 0;
				z = 0;
				for( span=3; span<16; span++ )
				{
					x0 = pMem.read(span+0x1080);
					y0 = pMem.read(span+0x1480);
					y1 = pMem.read(span+0x1400);
	
					if( y>=y0 && y<=y1 && x>=x0 && x0>=z )
					{
						pen = pMem.read(span+0x1280)&0xf;
						z = x0; /* give priority to rightmost spans */
					}
				}
				color = Machine.pens.read(pen);
				plot_pixel.handler(bitmap, x, 0xff-y, color );
			}
		}
		return;
	}
	
	/* "shell" graphics are 16x16 pixel tiles used for player shots and targeting cursor */
	static void draw_shell(
			mame_bitmap bitmap,
			int picture_code,
			int hposition,
			int vstart,
			int vstop,
			int vstretch,
			int hstretch )
	{
		if( hstretch != 0 )
		{
			int sx,sy;
			for( sx=0; sx<256; sx+=16 )
			{
				for( sy=0; sy<256; sy+=16 )
				{
					drawgfx( bitmap, Machine.gfx[1],
						picture_code,
						0, /* color */
						0,0, /* flip */
						sx,sy,
						Machine.visible_area,
						TRANSPARENCY_PEN,0 );
				}
			}
		}
		else
		/*
			vstretch is normally 0x01
	
			targeting cursor:
				hposition	= 0x78
				vstart		= 0x90
				vstop		= 0x80
	
			during grid test:
				vstretch	= 0xff
				hposition	= 0xff
				vstart		= 0xff
				vstop		= 0x00
	
		*/
		drawgfx( bitmap, Machine.gfx[1],
				picture_code,
				0, /* color */
				0,0, /* flip */
				255-hposition-16,vstart-32,
				Machine.visible_area,
				TRANSPARENCY_PEN,0 );
	}
	
	public static VhUpdatePtr tunhunt_vh_screenrefresh = new VhUpdatePtr() { public void handler(mame_bitmap bitmap,int full_refresh) 
	{
		UBytePtr pMem = new UBytePtr(memory_region( REGION_CPU1 ));
	
		update_palette();
	
		draw_box( bitmap );
	
		draw_motion_object( bitmap );
	
		draw_shell( bitmap,
			pMem.read(SHL0PC),	/* picture code */
			pMem.read(SHEL0H),	/* hposition */
			pMem.read(SHL0V),	/* vstart */
			pMem.read(SHL0VS),	/* vstop */
			pMem.read(SHL0ST),	/* vstretch */
			tunhunt_control&0x08 ); /* hstretch */
	
		draw_shell( bitmap,
			pMem.read(SHL1PC),	/* picture code */
			pMem.read(SHEL1H),	/* hposition */
			pMem.read(SHL1V),	/* vstart */
			pMem.read(SHL1VS),	/* vstop */
			pMem.read(SHL1ST),	/* vstretch */
			tunhunt_control&0x10 ); /* hstretch */
	
		draw_text( bitmap );
	} };
	
	public static ReadHandlerPtr tunhunt_button_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int data = readinputport( 0 );
		return ((data>>offset)&1)!=0?0x00:0x80;
	} };
	public static ReadHandlerPtr dsw1_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return readinputport(3)&0xff;
	} };
	public static ReadHandlerPtr dsw2_0r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return (readinputport(3)&0x0100)!=0?0x80:0x00;
	} };
	public static ReadHandlerPtr dsw2_1r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return (readinputport(3)&0x0200)!=0?0x80:0x00;
	} };
	public static ReadHandlerPtr dsw2_2r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return (readinputport(3)&0x0400)!=0?0x80:0x00;
	} };
	public static ReadHandlerPtr dsw2_3r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return (readinputport(3)&0x0800)!=0?0x80:0x00;
	} };
	public static ReadHandlerPtr dsw2_4r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return (readinputport(3)&0x1000)!=0?0x80:0x00;
	} };
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x03ff, MRA_RAM ), /* Work RAM */
		new Memory_ReadAddress( 0x2000, 0x2007, tunhunt_button_r ),
		new Memory_ReadAddress( 0x3000, 0x300f, pokey1_r ),
		new Memory_ReadAddress( 0x4000, 0x400f, pokey2_r ),
		new Memory_ReadAddress( 0x5000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0xfffa, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x03ff, MWA_RAM ), /* Work RAM */
		new Memory_WriteAddress( 0x1080, 0x10ff, MWA_RAM ),
		new Memory_WriteAddress( 0x1200, 0x12ff, MWA_RAM ),
		new Memory_WriteAddress( 0x1400, 0x14ff, MWA_RAM ),
		new Memory_WriteAddress( 0x1600, 0x160f, MWA_RAM, paletteram ), /* COLRAM (D7-D4 SHADE; D3-D0 COLOR) */
		new Memory_WriteAddress( 0x1800, 0x1800, MWA_RAM ), /* SHEL0H */
		new Memory_WriteAddress( 0x1a00, 0x1a00, MWA_RAM ), /* SHEL1H */
		new Memory_WriteAddress( 0x1c00, 0x1c00, MWA_RAM ), /* MOBJV */
		new Memory_WriteAddress( 0x1e00, 0x1eff, MWA_RAM, videoram ),	/* ALPHA */
		new Memory_WriteAddress( 0x2c00, 0x2fff, tunhunt_mott_w, spriteram ),
		new Memory_WriteAddress( 0x2000, 0x2000, MWA_NOP ), /* watchdog */
		new Memory_WriteAddress( 0x2400, 0x2400, MWA_NOP ), /* INT ACK */
		new Memory_WriteAddress( 0x2800, 0x2800, tunhunt_control_w ),
		new Memory_WriteAddress( 0x3000, 0x300f, pokey1_w ),
		new Memory_WriteAddress( 0x4000, 0x400f, pokey2_w ),
		new Memory_WriteAddress( 0x5000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	static InputPortPtr input_ports_tunhunt = new InputPortPtr(){ public void handler() { 
		PORT_START(); 
		PORT_BIT ( 0x01, IP_ACTIVE_HIGH, IPT_TILT );
		PORT_BIT ( 0x02, IP_ACTIVE_HIGH, IPT_BUTTON1 );
		PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Service_Mode") );
		PORT_DIPSETTING (	0x00, DEF_STR( "Off") );
		PORT_DIPSETTING (	0x04, DEF_STR( "On") );
		PORT_BIT ( 0x08, IP_ACTIVE_HIGH, IPT_COIN1 );
		PORT_BIT ( 0x10, IP_ACTIVE_HIGH, IPT_COIN2 );
		PORT_BIT ( 0x20, IP_ACTIVE_HIGH, IPT_BUTTON2 );
		PORT_BIT ( 0x40, IP_ACTIVE_HIGH, IPT_START1 );
		PORT_BIT ( 0x80, IP_ACTIVE_LOW, IPT_VBLANK );
	
		PORT_START(); 
		PORT_ANALOG( 0xff, 0x00, IPT_AD_STICK_Y, 100, 4, 0x00, 0xff );
	
		PORT_START(); 
		PORT_ANALOG( 0xff, 0x00, IPT_AD_STICK_X | IPF_REVERSE, 100, 4, 0x00, 0xff );
	
		PORT_START();  /* dip switches */
		PORT_DIPNAME (0x0003, 0x0002, DEF_STR( "Coinage") );
		PORT_DIPSETTING (     0x0003, DEF_STR( "2C_1C") );
		PORT_DIPSETTING (     0x0002, DEF_STR( "1C_1C") );
		PORT_DIPSETTING (     0x0001, DEF_STR( "1C_2C") );
		PORT_DIPSETTING (     0x0000, DEF_STR( "Free_Play") );
		PORT_DIPNAME (0x000c, 0x0000, DEF_STR( "Coin_B") );
		PORT_DIPSETTING (     0x0000, "*1" );
		PORT_DIPSETTING (     0x0004, "*4" );
		PORT_DIPSETTING (     0x0008, "*5" );
		PORT_DIPSETTING (     0x000c, "*6" );
		PORT_DIPNAME (0x0010, 0x0000, DEF_STR( "Coin_A") );
		PORT_DIPSETTING (     0x0000, "*1" );
		PORT_DIPSETTING (     0x0010, "*2" );
		PORT_DIPNAME (0x0060, 0x0000, "Bonus Credits" );
		PORT_DIPSETTING (     0x0000, "None" );
		PORT_DIPSETTING (     0x0060, "5 credits, 1 bonus" );
		PORT_DIPSETTING (     0x0040, "4 credits, 1 bonus" );
		PORT_DIPSETTING (     0x0020, "2 credits, 1 bonus" );
		PORT_DIPNAME (0x0880, 0x0000, "Language" );
		PORT_DIPSETTING (     0x0000, "English" );
		PORT_DIPSETTING (     0x0080, "German" );
		PORT_DIPSETTING (     0x0800, "French" );
		PORT_DIPSETTING (     0x0880, "Spanish" );
		PORT_DIPNAME (0x0100, 0x0000, DEF_STR( "Unknown") );
		PORT_DIPSETTING (     0x0000, DEF_STR( "Off") );
		PORT_DIPSETTING (     0x0100, DEF_STR( "On") );
		PORT_DIPNAME (0x0600, 0x0200, DEF_STR( "Lives") );
		PORT_DIPSETTING (     0x0000, "2" );
		PORT_DIPSETTING (     0x0200, "3" );
		PORT_DIPSETTING (     0x0400, "4" );
		PORT_DIPSETTING (     0x0600, "5" );
		PORT_DIPNAME (0x1000, 0x1000, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING (     0x1000, "30000" );
		PORT_DIPSETTING (     0x0000, "None" );
	INPUT_PORTS_END(); }}; 
	
	static POKEYinterface pokey_interface = new POKEYinterface
	(
		2,	/* 2 chips */
		1209600,
		new int[] { 50, 50 }, /* volume */
		/* pot handlers */
		new ReadHandlerPtr[] { null, input_port_1_r },
		new ReadHandlerPtr[] { null, input_port_2_r },
		new ReadHandlerPtr[] { null, dsw2_0r },
		new ReadHandlerPtr[] { null, dsw2_1r },
		new ReadHandlerPtr[] { null, dsw2_2r },
		new ReadHandlerPtr[] { null, dsw2_3r },
		new ReadHandlerPtr[] { null, dsw2_4r },
		new ReadHandlerPtr[] { null, null },
		new ReadHandlerPtr[] { dsw1_r, null }
	);
	
	static MachineDriver machine_driver_tunhunt = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6502,
				2000000, /* ??? */
				readmem,writemem,null,null,
				interrupt,2 /* ? probably wrong */
                        )
		},
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		10,
		null,
		/* video hardware */
		256, 256-16, new rectangle( 0, 255, 0, 255-16 ),
		gfxdecodeinfo,
		16,16, /* number of colors, colortable size */
		tunhunt_vh_convert_color_prom,
		VIDEO_TYPE_RASTER,
		null,
		tunhunt_vh_start,
		tunhunt_vh_stop,
		tunhunt_vh_screenrefresh,
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_POKEY,
				pokey_interface
                       )
		}
	);
	
	/**************************************************************************/
	
	static RomLoadPtr rom_tunhunt = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );
		ROM_LOAD( "001.lm1",	0x5000, 0x800, 0x2601a3a4 );
		ROM_LOAD( "002.k1",		0x5800, 0x800, 0x29bbf3df );
		/*TODO*///ROM_LOAD( "003.j1",		0x6000, 0x800, 0x360c0f47 );/* bad crc? fails self-test */
						/* 0xcaa6bb2a: alternate prom (re)dumped by Al also fails */
		ROM_LOAD( "004.fh1",	0x6800, 0x800, 0x4d6c920e );
		ROM_LOAD( "005.ef1",	0x7000, 0x800, 0xe17badf0 );
		ROM_LOAD( "006.d1",		0x7800, 0x800, 0xc3ae8519 );
		ROM_RELOAD( 		  	0xf800, 0x800 );/* 6502 vectors  */
	
		ROM_REGION( 0x400, REGION_GFX1, ROMREGION_DISPOSE );/* alphanumeric characters */
		ROM_LOAD( "019.c10",	0x000, 0x400, 0xd6fd45a9 );
	
		ROM_REGION( 0x400, REGION_GFX2, 0 );/* "SHELL" objects (16x16 pixel sprites) */
		ROM_LOAD( "016.a8",		0x000, 0x200, 0x830e6c34 );
		ROM_LOAD( "017.b8",		0x200, 0x200, 0x5bef8b5a );
	
		ROM_REGION( 0x540, REGION_PROMS, 0 );
		ROM_LOAD( "013.d11",	0x000, 0x020, 0x66f1f5eb );/* hue: BBBBGGGG? */
		ROM_LOAD( "014.c11",	0x020, 0x020, 0x662444b2 );/* hue: RRRR----? */
		ROM_LOAD( "015.n4",		0x040, 0x100, 0x00e224a0 );/* timing? */
		ROM_LOAD( "018.h9",		0x140, 0x400, 0x6547c208 );/* color lookup table? */
	
	ROM_END(); }}; 
	
	/*         rom   parent  machine    inp       	init */
	public static GameDriver driver_tunhunt	   = new GameDriver("1981"	,"tunhunt"	,"tunhunt.java"	,rom_tunhunt,null	,machine_driver_tunhunt	,input_ports_tunhunt	,null	,ORIENTATION_SWAP_XY	,	"Atari", "Tunnel Hunt" );
}
