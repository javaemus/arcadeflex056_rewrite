/***************************************************************************

Rally X memory map (preliminary)

driver by Nicola Salmoria


0000-3fff ROM
8000-83ff Radar video RAM + other
8400-87ff video RAM
8800-8bff Radar color RAM + other
8c00-8fff color RAM
9800-9fff RAM

memory mapped ports:

read:
a000	  IN0
a080	  IN1
a100	  DSW1

write:
8014-801f sprites - 6 pairs: code (including flipping) and X position
8814-881f sprites - 6 pairs: Y position and color
8034-880c radar car indicators x position
8834-883c radar car indicators y position
a004-a00c radar car indicators color and x position MSB
a080	  watchdog reset
a105	  sound voice 1 waveform (nibble)
a111-a113 sound voice 1 frequency (nibble)
a115	  sound voice 1 volume (nibble)
a10a	  sound voice 2 waveform (nibble)
a116-a118 sound voice 2 frequency (nibble)
a11a	  sound voice 2 volume (nibble)
a10f	  sound voice 3 waveform (nibble)
a11b-a11d sound voice 3 frequency (nibble)
a11f	  sound voice 3 volume (nibble)
a130	  virtual screen X scroll position
a140	  virtual screen Y scroll position
a170	  ? this is written to A LOT of times every frame
a180	  explosion sound trigger
a181	  interrupt enable
a182	  ?
a183	  flip screen
a184	  1 player start lamp
a185	  2 players start lamp
a186	  coin lockout
a187	  coin counter

I/O ports:
OUT on port $0 sets the interrupt vector/instruction (the game uses both
IM 2 and IM 0)

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP2.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;
import WIP2.common.ptr.UBytePtr;

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
import static WIP2.mame056.inputH.*;

import static WIP2.mame056.sndintrfH.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sound.samples.*;
import static WIP2.mame056.sound.samplesH.*;

import static WIP2.mame056.vidhrdw.generic.*;

import static WIP2.arcadeflex056.fileio.*;
import static mame056.palette.*;
import static WIP2.mame056.sound.namco.*;
import static WIP2.mame056.sound.namcoH.*;
import static WIP2.mame056.vidhrdw.rallyx.*;

public class rallyx
{
	
	public static WriteHandlerPtr rallyx_coin_lockout_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		coin_lockout_w(offset, data ^ 1);
	} };
	
	public static WriteHandlerPtr rallyx_coin_counter_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		coin_counter_w.handler(offset, data);
	} };
	
	public static WriteHandlerPtr rallyx_leds_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		/*TODO*///set_led_status(offset,data & 1);
	} };
	
        static int last;
        
	public static WriteHandlerPtr rallyx_play_sound_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{           
		if ((data == 0) && (last != 0))
			sample_start(0,0,0);
	
		last = data;
	} };
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x3fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0x8fff, MRA_RAM ),
		new Memory_ReadAddress( 0x9800, 0x9fff, MRA_RAM ),
		new Memory_ReadAddress( 0xa000, 0xa000, input_port_0_r ), /* IN0 */
		new Memory_ReadAddress( 0xa080, 0xa080, input_port_1_r ), /* IN1 */
		new Memory_ReadAddress( 0xa100, 0xa100, input_port_2_r ), /* DSW1 */
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x3fff, MWA_ROM ),
		new Memory_WriteAddress( 0x8000, 0x83ff, videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0x8400, 0x87ff, rallyx_videoram2_w, rallyx_videoram2 ),
		new Memory_WriteAddress( 0x8800, 0x8bff, colorram_w, colorram ),
		new Memory_WriteAddress( 0x8c00, 0x8fff, rallyx_colorram2_w, rallyx_colorram2 ),
		new Memory_WriteAddress( 0x9800, 0x9fff, MWA_RAM ),
		new Memory_WriteAddress( 0xa004, 0xa00f, MWA_RAM, rallyx_radarattr ),
		new Memory_WriteAddress( 0xa080, 0xa080, watchdog_reset_w ),
		new Memory_WriteAddress( 0xa100, 0xa11f, pengo_sound_w, namco_soundregs ),
		new Memory_WriteAddress( 0xa130, 0xa130, MWA_RAM, rallyx_scrollx ),
		new Memory_WriteAddress( 0xa140, 0xa140, MWA_RAM, rallyx_scrolly ),
		//new Memory_WriteAddress( 0xa170, 0xa170, MWA_NOP ),	/* ????? */
		new Memory_WriteAddress( 0xa180, 0xa180, rallyx_play_sound_w ),
		new Memory_WriteAddress( 0xa181, 0xa181, interrupt_enable_w ),
		new Memory_WriteAddress( 0xa183, 0xa183, rallyx_flipscreen_w ),
		new Memory_WriteAddress( 0xa184, 0xa185, rallyx_leds_w ),
		new Memory_WriteAddress( 0xa186, 0xa186, rallyx_coin_lockout_w ),
		new Memory_WriteAddress( 0xa187, 0xa187, rallyx_coin_counter_w ),
		new Memory_WriteAddress( 0x8014, 0x801f, MWA_RAM, spriteram, spriteram_size ),	/* these are here just to initialize */
		new Memory_WriteAddress( 0x8814, 0x881f, MWA_RAM, spriteram_2 ),	/* the pointers. */
		new Memory_WriteAddress( 0x8034, 0x803f, MWA_RAM, rallyx_radarx, rallyx_radarram_size ), /* ditto */
		new Memory_WriteAddress( 0x8834, 0x883f, MWA_RAM, rallyx_radary ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static IO_WritePort writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_WritePort( 0, 0, interrupt_vector_w ),
		new IO_WritePort(MEMPORT_MARKER, 0)
	};
	
	
	
	static InputPortPtr input_ports_rallyx = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_COIN3 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT |IPF_4WAY );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_COIN1 );
	
		PORT_START(); 		/* IN1 */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(	0x01, DEF_STR( "Upright") );
		PORT_DIPSETTING(	0x00, DEF_STR( "Cocktail") );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_COIN2 );
	
		PORT_START(); 		/* DSW0 */
		PORT_SERVICE( 0x01, IP_ACTIVE_LOW );
		/* TODO: the bonus score depends on the number of lives */
		PORT_DIPNAME( 0x06, 0x02, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(	0x02, "A" );
		PORT_DIPSETTING(	0x04, "B" );
		PORT_DIPSETTING(	0x06, "C" );
		PORT_DIPSETTING(	0x00, "None" );
		PORT_DIPNAME( 0x38, 0x08, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(	0x10, "1 Car, Medium" );
		PORT_DIPSETTING(	0x28, "1 Car, Hard" );
		PORT_DIPSETTING(	0x00, "2 Cars, Easy" );
		PORT_DIPSETTING(	0x18, "2 Cars, Medium" );
		PORT_DIPSETTING(	0x30, "2 Cars, Hard" );
		PORT_DIPSETTING(	0x08, "3 Cars, Easy" );
		PORT_DIPSETTING(	0x20, "3 Cars, Medium" );
		PORT_DIPSETTING(	0x38, "3 Cars, Hard" );
		PORT_DIPNAME( 0xc0, 0xc0, DEF_STR( "Coinage") );
		PORT_DIPSETTING(	0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(	0xc0, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(	0x80, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(	0x00, DEF_STR( "Free_Play") );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_nrallyx = new InputPortPtr(){ public void handler() { 
		PORT_START(); 		/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_COIN3 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_4WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT |IPF_4WAY );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_COIN1 );
	
		PORT_START(); 		/* IN1 */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(	0x01, DEF_STR( "Upright") );
		PORT_DIPSETTING(	0x00, DEF_STR( "Cocktail") );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_4WAY | IPF_COCKTAIL );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_COIN2 );
	
		PORT_START(); 		/* DSW0 */
		PORT_SERVICE( 0x01, IP_ACTIVE_LOW );
		/* TODO: the bonus score depends on the number of lives */
		PORT_DIPNAME( 0x06, 0x02, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(	0x02, "A" );
		PORT_DIPSETTING(	0x04, "B" );
		PORT_DIPSETTING(	0x06, "C" );
		PORT_DIPSETTING(	0x00, "None" );
		PORT_DIPNAME( 0x38, 0x00, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(	0x10, "1 Car, Medium" );
		PORT_DIPSETTING(	0x28, "1 Car, Hard" );
		PORT_DIPSETTING(	0x18, "2 Cars, Medium" );
		PORT_DIPSETTING(	0x30, "2 Cars, Hard" );
		PORT_DIPSETTING(	0x00, "3 Cars, Easy" );
		PORT_DIPSETTING(	0x20, "3 Cars, Medium" );
		PORT_DIPSETTING(	0x38, "3 Cars, Hard" );
		PORT_DIPSETTING(	0x08, "4 Cars, Easy" );
		PORT_DIPNAME( 0xc0, 0xc0, DEF_STR( "Coinage") );
		PORT_DIPSETTING(	0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(	0xc0, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(	0x80, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(	0x00, DEF_STR( "Free_Play") );
	INPUT_PORTS_END(); }}; 
	
	
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,	/* 8*8 characters */
		256,	/* 256 characters */
		2,	/* 2 bits per pixel */
		new int[] { 0, 4 },	/* the two bitplanes for 4 pixels are packed into one byte */
		new int[] { 8*8+0, 8*8+1, 8*8+2, 8*8+3, 0, 1, 2, 3 }, /* bits are packed in groups of four */
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		16*8	/* every char takes 16 bytes */
	);
	static GfxLayout spritelayout = new GfxLayout
	(
		16,16,	/* 16*16 sprites */
		64, /* 64 sprites */
		2,	/* 2 bits per pixel */
		new int[] { 0, 4 },	/* the two bitplanes for 4 pixels are packed into one byte */
		new int[] { 8*8+0, 8*8+1, 8*8+2, 8*8+3, 16*8+0, 16*8+1, 16*8+2, 16*8+3,	/* bits are packed in groups of four */
				 24*8+0, 24*8+1, 24*8+2, 24*8+3, 0, 1, 2, 3 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8,
				32*8, 33*8, 34*8, 35*8, 36*8, 37*8, 38*8, 39*8 },
		64*8	/* every sprite takes 64 bytes */
	);
	
	static GfxLayout dotlayout = new GfxLayout
	(
		4,4,	/* 4*4 characters */
		8,	/* 8 characters */
		2,	/* 2 bits per pixel */
		new int[] { 6, 7 },
		new int[] { 3*8, 2*8, 1*8, 0*8 },
		new int[] { 3*32, 2*32, 1*32, 0*32 },
		16*8	/* every char takes 16 consecutive bytes */
	);
	
	
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, charlayout,		  0, 64 ),
		new GfxDecodeInfo( REGION_GFX1, 0, spritelayout,	  0, 64 ),
		new GfxDecodeInfo( REGION_GFX2, 0, dotlayout,	   64*4,  1 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	
	
	static namco_interface namco_interface = new namco_interface
	(
		3072000/32, /* sample rate */
		3,			/* number of voices */
		100,		/* playback volume */
		REGION_SOUND1	/* memory region */
        );
	
	static String rallyx_sample_names[] =
	{
		"*rallyx",
		"bang.wav",
		null	/* end of array */
	};
	
	static Samplesinterface samples_interface = new Samplesinterface
        (
		1,	/* 1 channel */
		80, /* volume */
		rallyx_sample_names
        );
	
	
	
	static MachineDriver machine_driver_rallyx = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				3072000,	/* 3.072 MHz ? */
				readmem,writemem,null,writeport,
				interrupt,1
			)
		},
		60.606060f, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
		36*8, 28*8, new rectangle( 0*8, 36*8-1, 0*8, 28*8-1 ),
		gfxdecodeinfo,
		32,64*4+4,
		rallyx_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		rallyx_vh_start,
		rallyx_vh_stop,
		rallyx_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_NAMCO,
				namco_interface
			),
			new MachineSound(
				SOUND_SAMPLES,
				samples_interface
			)
		}
	);
	
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	static RomLoadPtr rom_rallyx = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "1b",           0x0000, 0x1000, 0x5882700d );
		ROM_LOAD( "rallyxn.1e",   0x1000, 0x1000, 0xed1eba2b );
		ROM_LOAD( "rallyxn.1h",   0x2000, 0x1000, 0x4f98dd1c );
		ROM_LOAD( "rallyxn.1k",   0x3000, 0x1000, 0x9aacccf0 );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "8e",           0x0000, 0x1000, 0x277c1de5 );
	
		ROM_REGION( 0x0100, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "im5623.8m",    0x0000, 0x0100, 0x3c16f62c ); /* dots */
	
		ROM_REGION( 0x0120, REGION_PROMS, 0 );
		ROM_LOAD( "m3-7603.11n",  0x0000, 0x0020, 0xc7865434 );
		ROM_LOAD( "im5623.8p",    0x0020, 0x0100, 0x834d4fda );
	
		ROM_REGION( 0x0200, REGION_SOUND1, 0 );/* sound proms */
		ROM_LOAD( "im5623.3p",    0x0000, 0x0100, 0x4bad7017 );
		ROM_LOAD( "im5623.2m",    0x0100, 0x0100, 0x77245b66 ); /* timing - not used */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_rallyxm = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "1b",           0x0000, 0x1000, 0x5882700d );
		ROM_LOAD( "1e",           0x1000, 0x1000, 0x786585ec );
		ROM_LOAD( "1h",           0x2000, 0x1000, 0x110d7dcd );
		ROM_LOAD( "1k",           0x3000, 0x1000, 0x473ab447 );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "8e",           0x0000, 0x1000, 0x277c1de5 );
	
		ROM_REGION( 0x0100, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "im5623.8m",    0x0000, 0x0100, 0x3c16f62c ); /* dots */
	
		ROM_REGION( 0x0120, REGION_PROMS, 0 );
		ROM_LOAD( "m3-7603.11n",  0x0000, 0x0020, 0xc7865434 );
		ROM_LOAD( "im5623.8p",    0x0020, 0x0100, 0x834d4fda );
	
		ROM_REGION( 0x0200, REGION_SOUND1, 0 );/* sound proms */
		ROM_LOAD( "im5623.3p",    0x0000, 0x0100, 0x4bad7017 );
		ROM_LOAD( "im5623.2m",    0x0100, 0x0100, 0x77245b66 ); /* timing - not used */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_nrallyx = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "nrallyx.1b",   0x0000, 0x1000, 0x9404c8d6 );
		ROM_LOAD( "nrallyx.1e",   0x1000, 0x1000, 0xac01bf3f );
		ROM_LOAD( "nrallyx.1h",   0x2000, 0x1000, 0xaeba29b5 );
		ROM_LOAD( "nrallyx.1k",   0x3000, 0x1000, 0x78f17da7 );
	
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "nrallyx.8e",   0x0000, 0x1000, 0xca7a174a );
	
		ROM_REGION( 0x0100, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "im5623.8m",    0x0000, 0x0100, 0x3c16f62c );   /* dots */
	
		ROM_REGION( 0x0120, REGION_PROMS, 0 );
		ROM_LOAD( "nrallyx.pr1",  0x0000, 0x0020, 0xa0a49017 );
		ROM_LOAD( "nrallyx.pr2",  0x0020, 0x0100, 0xb2b7ca15 );
	
		ROM_REGION( 0x0200, REGION_SOUND1, 0 );/* sound proms */
		ROM_LOAD( "nrallyx.spr",  0x0000, 0x0100, 0xb75c4e87 );
		ROM_LOAD( "im5623.2m",    0x0100, 0x0100, 0x77245b66 ); /* timing - not used */
	ROM_END(); }}; 
	
	
	
	public static GameDriver driver_rallyx	   = new GameDriver("1980"	,"rallyx"	,"rallyx.java"	,rom_rallyx,null	,machine_driver_rallyx	,input_ports_rallyx	,null	,ROT0	,	"Namco", "Rally X" );
	public static GameDriver driver_rallyxm	   = new GameDriver("1980"	,"rallyxm"	,"rallyx.java"	,rom_rallyxm,driver_rallyx	,machine_driver_rallyx	,input_ports_rallyx	,null	,ROT0	,	"[Namco] (Midway license)", "Rally X (Midway)" );
	public static GameDriver driver_nrallyx	   = new GameDriver("1981"	,"nrallyx"	,"rallyx.java"	,rom_nrallyx,null	,machine_driver_rallyx	,input_ports_nrallyx	,null	,ROT0	,	"Namco", "New Rally X" );
	
}
