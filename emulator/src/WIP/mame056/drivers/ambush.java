/***************************************************************************

Ambush Memory Map (preliminary)

driver by Zsolt Vasvari


Memory Mapped:

0000-7fff   R	ROM
8000-87ff	R/W	RAM
a000		R	Watchdog Reset
c080-c09f	W	Scroll RAM (1 byte for each column)
c100-c1ff	W   Color RAM (1 line corresponds to 4 in the video ram)
c200-c3ff   W   Sprite RAM
c400-c7ff   W   Video RAM
c800		R   DIP Switches
cc00-cc03   W   ??? (Maybe analog sound triggers?)
cc04		W   Flip Screen
cc05		W   Color Bank Select
cc07		W   Coin Counter


I/O Ports:

00-01		R/W AY8910 #0 (Port A = Input Port #0)
80-81		R/W AY8910 #1 (Port A = Input Port #1)


TODO:

- Verify Z80 and AY8910 clock speeds

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP2.common.libc.cstring.*;
import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.arcadeflex056.fileio.*;
import static WIP2.common.ptr.UBytePtr;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.memory.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sndintrfH.*;

import static WIP.mame056.vidhrdw.ambush.*;
import static WIP2.mame056.sound.ay8910.*;
import static WIP2.mame056.sound.ay8910H.*;
import static WIP2.mame056.vidhrdw.generic.*;

public class ambush
{
	
	public static WriteHandlerPtr ambush_coin_counter_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		coin_counter_w.handler(0, data & 0x01);
		coin_counter_w.handler(1, data & 0x02);
	} };
	
	public static WriteHandlerPtr flip_screen_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		flip_screen_set(data);
	} };
	
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0x87ff, MRA_RAM ),
		new Memory_ReadAddress( 0xa000, 0xa000, watchdog_reset_r ),
		new Memory_ReadAddress( 0xc000, 0xc7ff, MRA_RAM ),
		new Memory_ReadAddress( 0xc800, 0xc800, input_port_2_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x8000, 0x87ff, MWA_RAM ),
		new Memory_WriteAddress( 0xc080, 0xc09f, MWA_RAM, ambush_scrollram ),
		new Memory_WriteAddress( 0xc100, 0xc1ff, MWA_RAM, colorram ),
		new Memory_WriteAddress( 0xc200, 0xc3ff, MWA_RAM, spriteram, spriteram_size ),
		new Memory_WriteAddress( 0xc400, 0xc7ff, MWA_RAM, videoram, videoram_size ),
		new Memory_WriteAddress( 0xcc00, 0xcc03, MWA_NOP ),
		new Memory_WriteAddress( 0xcc04, 0xcc04, flip_screen_w ),
		new Memory_WriteAddress( 0xcc05, 0xcc05, MWA_RAM, ambush_colorbank ),
		new Memory_WriteAddress( 0xcc07, 0xcc07, ambush_coin_counter_w ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static IO_ReadPort readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_ReadPort( 0x00, 0x00, AY8910_read_port_0_r ),
		new IO_ReadPort( 0x80, 0x80, AY8910_read_port_1_r ),
		new IO_ReadPort(MEMPORT_MARKER, 0)
	};
	
	public static IO_WritePort writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_WritePort( 0x00, 0x00, AY8910_control_port_0_w ),
		new IO_WritePort( 0x01, 0x01, AY8910_write_port_0_w ),
		new IO_WritePort( 0x80, 0x80, AY8910_control_port_1_w ),
		new IO_WritePort( 0x81, 0x81, AY8910_write_port_1_w ),
		new IO_WritePort(MEMPORT_MARKER, 0)
	};
	
	
	static InputPortPtr input_ports_ambush = new InputPortPtr(){ public void handler() { 
		PORT_START();       /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_COIN1 );
	
		PORT_START();       /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP    | IPF_8WAY );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_UP    | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_COCKTAIL );
	
		PORT_START();       /* DSW */
		PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "3" );
		PORT_DIPSETTING(    0x01, "4" );
		PORT_DIPSETTING(    0x02, "5" );
		PORT_DIPSETTING(    0x03, "6" );
		PORT_DIPNAME( 0x1c, 0x00, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0x14, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x18, DEF_STR( "2C_5C") );
		PORT_DIPSETTING(    0x08, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x0c, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x1c, "Service Mode/Free Play" );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x00, "Easy" );
		PORT_DIPSETTING(    0x20, "Hard" );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x40, "80000" );
		PORT_DIPSETTING(    0x00, "120000" );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Cocktail") );
	INPUT_PORTS_END(); }}; 
	
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,    /* 8*8 chars */
		1024,   /* 2048 characters */
		2,      /* 2 bits per pixel */
		new int[] { 0, 0x2000*8 },  /* The bitplanes are seperate */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7},
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8},
		8*8     /* every char takes 8 consecutive bytes */
	);
	
	static GfxLayout spritelayout = new GfxLayout
	(
		16,16,  /* 8*8 chars */
		256,    /* 2048 characters */
		2,      /* 2 bits per pixel */
		new int[] { 0, 0x2000*8 },  /* The bitplanes are seperate */
		new int[] {     0,     1,     2,     3,     4,     5,     6,     7,
		  8*8+0, 8*8+1, 8*8+2, 8*8+3, 8*8+4, 8*8+5, 8*8+6, 8*8+7 },
		new int[] {  0*8,  1*8,  2*8,  3*8,  4*8,  5*8,  6*8,  7*8,
		  16*8, 17*8, 18*8, 19*8, 20*8, 21*8, 22*8, 23*8 },
		32*8     /* every char takes 32 consecutive bytes */
	);
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0x0000, charlayout,   0, 64 ),
		new GfxDecodeInfo( REGION_GFX1, 0x0000, spritelayout, 0, 64 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	
	static AY8910interface ay8910_interface = new AY8910interface
	(
		2,	/* 2 chips */
		1500000,	/* 1.5 MHz ? */
		new int[] { 25, 25 },
		new ReadHandlerPtr[] { input_port_0_r, input_port_1_r },
		new ReadHandlerPtr[] { null, null },
		new WriteHandlerPtr[] { null, null },
		new WriteHandlerPtr[] { null, null }
	);
	
	
	static MachineDriver machine_driver_ambush = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				4000000,        /* 4.00 MHz??? */
				readmem,writemem,readport,writeport,
				interrupt,1
			)
		},
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,  /* frames per second, vblank duration */
		1,      /* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 30*8-3 ),  /* The -3 makes the cocktail mode perfect */
		gfxdecodeinfo,
		256, 0,
		ambush_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		generic_vh_start,
		generic_vh_stop,
		ambush_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_AY8910,
				ay8910_interface
			)
		}
	);
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	static RomLoadPtr rom_ambush = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );      /* 64k for code */
		ROM_LOAD( "ambush.h7",    0x0000, 0x2000, 0xce306563 );
		ROM_LOAD( "ambush.g7",    0x2000, 0x2000, 0x90291409 );
		ROM_LOAD( "ambush.f7",    0x4000, 0x2000, 0xd023ca29 );
		ROM_LOAD( "ambush.e7",    0x6000, 0x2000, 0x6cc2d3ee );
	
		ROM_REGION( 0x4000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "ambush.n4",    0x0000, 0x2000, 0xecc0dc85 );
		ROM_LOAD( "ambush.m4",    0x2000, 0x2000, 0xe86ca98a );
	
		ROM_REGION( 0x0400, REGION_PROMS, 0 );
		ROM_LOAD( "a.bpr",        0x0000, 0x0100, 0x5f27f511 ); /* color PROMs */
	
		ROM_LOAD( "b.bpr",        0x0100, 0x0100, 0x1b03fd3b );/* How is this selected, */
		ROM_LOAD( "13.bpr",		  0x0200, 0x0100, 0x547e970f ); /* I'm not sure what these do. */
		ROM_LOAD( "14.bpr",		  0x0300, 0x0100, 0x622a8ce7 ); /* They don't look like color PROMs */
	ROM_END(); }}; 
	
	
	
	public static GameDriver driver_ambush	   = new GameDriver("1983"	,"ambush"	,"ambush.java"	,rom_ambush,null	,machine_driver_ambush	,input_ports_ambush	,null	,ROT0	,	"Nippon Amuse Co-Ltd", "Ambush" );
}
