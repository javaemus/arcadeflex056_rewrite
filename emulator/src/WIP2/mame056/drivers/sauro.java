/***************************************************************************

Sauro Memory Map (preliminary)

driver by Zsolt Vasvari

Main CPU
--------

Memory mapped:

0000-dfff	ROM
e000-e7ff	RAM
e800-ebff	Sprite RAM
f000-fbff	Background Video RAM
f400-ffff	Background Color RAM
f800-fbff	Foreground Video RAM
fc00-ffff	Foreground Color RAM

Ports:

00		R	DSW #1
20		R	DSW #2
40		R	Input Ports Player 1
60		R   Input Ports Player 2
80		 W  Sound Commnand
c0		 W  Flip Screen
c1		 W  ???
c2-c4	 W  ???
c6-c7	 W  ??? (Loads the sound latch?)
c8		 W	???
c9		 W	???
ca-cd	 W  ???
ce		 W  ???
e0		 W	Watchdog


Sound CPU
---------

Memory mapped:

0000-7fff		ROM
8000-87ff		RAM
a000	     W  ADPCM trigger
c000-c001	 W	YM3812
e000		R   Sound latch
e000-e006	 W  ???
e00e-e00f	 W  ???


TODO
----

- The readme claims there is a GI-SP0256A-AL ADPCM on the PCB. Needs to be
  emulated.

- Verify all clock speeds

- I'm only using colors 0-15. The other 3 banks are mostly the same, but,
  for example, the color that's used to paint the gradients of the sky (color 2)
  is different, so there might be a palette select. I don't see anything
  obviously wrong the way it is right now. It matches the screen shots found
  on the Spanish Dump site.

- What do the rest of the ports in the range c0-ce do?

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP2.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;
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
import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.common.libc.cstring.*;

// refactor
import static WIP2.arcadeflex036.osdepend.logerror;

import static WIP2.mame056.vidhrdw.sauro.*;
import static WIP2.mame056.sound._3812intfH.*;
import static WIP2.mame056.sound._3812intf.*;

public class sauro
{
	
	
	public static UBytePtr sauro_videoram2 = new UBytePtr();
	public static UBytePtr sauro_colorram2 = new UBytePtr();
	
	
	
	public static WriteHandlerPtr sauro_sound_command_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		data |= 0x80;
		soundlatch_w.handler(offset,data);
	} };
	
	public static ReadHandlerPtr sauro_sound_command_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int ret	= soundlatch_r.handler(offset);
		soundlatch_clear_w.handler(offset,0);
		return ret;
	} };
	
	public static WriteHandlerPtr flip_screen_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		flip_screen_set(data);
	} };
	
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),        
                new Memory_ReadAddress( 0x0000, 0xdfff, MRA_ROM ),
		new Memory_ReadAddress( 0xe000, 0xebff, MRA_RAM ),
		new Memory_ReadAddress( 0xf000, 0xffff, MRA_RAM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),        
                new Memory_WriteAddress( 0x0000, 0xdfff, MWA_ROM ),
		new Memory_WriteAddress( 0xe000, 0xe7ff, MWA_RAM ),
		new Memory_WriteAddress( 0xe800, 0xebff, MWA_RAM, spriteram, spriteram_size ),
		new Memory_WriteAddress( 0xf000, 0xf3ff, videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0xf400, 0xf7ff, colorram_w, colorram ),
		new Memory_WriteAddress( 0xf800, 0xfbff, MWA_RAM, sauro_videoram2 ),
		new Memory_WriteAddress( 0xfc00, 0xffff, MWA_RAM, sauro_colorram2 ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static IO_ReadPort readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),		new IO_ReadPort( 0x00, 0x00, input_port_2_r ),
			new IO_ReadPort( 0x20, 0x20, input_port_3_r ),
			new IO_ReadPort( 0x40, 0x40, input_port_0_r ),
			new IO_ReadPort( 0x60, 0x60, input_port_1_r ),
		new IO_ReadPort(MEMPORT_MARKER, 0)
	};
	public static IO_WritePort writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),		
                        new IO_WritePort( 0xa0, 0xa0, sauro_scroll1_w ),
			new IO_WritePort( 0xa1, 0xa1, sauro_scroll2_w ),
			new IO_WritePort( 0x80, 0x80, sauro_sound_command_w ),
			new IO_WritePort( 0xc0, 0xc0, flip_screen_w ),
			new IO_WritePort( 0xc1, 0xce, MWA_NOP ),
			new IO_WritePort( 0xe0, 0xe0, watchdog_reset_w ),
		new IO_WritePort(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress sound_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),        new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
			new Memory_ReadAddress( 0x8000, 0x87ff, MRA_RAM ),
			new Memory_ReadAddress( 0xe000, 0xe000, sauro_sound_command_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress sound_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x8000, 0x87ff, MWA_RAM ),
		new Memory_WriteAddress( 0xc000, 0xc000, YM3812_control_port_0_w ),
		new Memory_WriteAddress( 0xc001, 0xc001, YM3812_write_port_0_w ),
	//	new Memory_WriteAddress( 0xa000, 0xa000, ADPCM_trigger ),
		new Memory_WriteAddress( 0xe000, 0xe006, MWA_NOP ),
		new Memory_WriteAddress( 0xe00e, 0xe00f, MWA_NOP ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	static InputPortPtr input_ports_sauro = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_BUTTON1 );	PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_BUTTON2 );	PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_COIN1 );	PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_COIN2 );	PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT  | IPF_8WAY );	PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_8WAY  );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP    | IPF_8WAY  );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN  | IPF_8WAY  );
		PORT_START();       /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_COCKTAIL );	PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_BUTTON2 | IPF_COCKTAIL);	PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_START1 );	PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_START2 );	PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT  | IPF_COCKTAIL | IPF_8WAY );	PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_COCKTAIL | IPF_8WAY  );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP    | IPF_COCKTAIL | IPF_8WAY  );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN  | IPF_COCKTAIL | IPF_8WAY  );
		PORT_START(); 
		PORT_SERVICE( 0x01, IP_ACTIVE_HIGH );	PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x02, DEF_STR( "On") );
		PORT_DIPNAME( 0x04, 0x04, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x04, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Free_Play") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x30, 0x20, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x30, "Very Easy" );	PORT_DIPSETTING(    0x20, "Easy" );	PORT_DIPSETTING(    0x10, "Hard" );                      /* This crashes test mode!!! */
		PORT_DIPSETTING(    0x00, "Very Hard" );	PORT_DIPNAME( 0x40, 0x40, "Allow Continue" );	PORT_DIPSETTING(    0x00, DEF_STR( "No") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Yes") );
		PORT_DIPNAME( 0x80, 0x00, "Freeze" );	PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x80, DEF_STR( "On") );
	
		PORT_START(); 
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x00, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "1C_1C") );
		PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x0c, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x08, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_5C") );
		PORT_DIPNAME( 0x30, 0x20, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x30, "2" );	PORT_DIPSETTING(    0x20, "3" );	PORT_DIPSETTING(    0x10, "4" );	PORT_DIPSETTING(    0x00, "5" );	PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x40, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x80, DEF_STR( "On") );
	INPUT_PORTS_END(); }}; 
	
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,	/* 8*8 chars */
	    2048,   /* 2048 characters */
	    4,      /* 4 bits per pixel */
	    new int[] { 0,1,2,3 },  /* The 4 planes are packed together */
	    new int[] { 0*4, 1*4, 2*4, 3*4, 4*4, 5*4, 6*4, 7*4},
	    new int[] { 0*4*8, 1*4*8, 2*4*8, 3*4*8, 4*4*8, 5*4*8, 6*4*8, 7*4*8},
	    8*8*4     /* every char takes 32 consecutive bytes */
	);
	
	static GfxLayout spritelayout = new GfxLayout
	(
		16,16,	/* 16*16 sprites */
	    1024,   /* 1024 sprites */
	    4,      /* 4 bits per pixel */
	    new int[] { 0,1,2,3 },  /* The 4 planes are packed together */
	    new int[] { 1*4, 0*4, 3*4, 2*4, 5*4, 4*4, 7*4, 6*4, 9*4, 8*4, 11*4, 10*4, 13*4, 12*4, 15*4, 14*4},
	    new int[] { 3*0x8000*8+0*4*16, 2*0x8000*8+0*4*16, 1*0x8000*8+0*4*16, 0*0x8000*8+0*4*16,
	      3*0x8000*8+1*4*16, 2*0x8000*8+1*4*16, 1*0x8000*8+1*4*16, 0*0x8000*8+1*4*16,
	      3*0x8000*8+2*4*16, 2*0x8000*8+2*4*16, 1*0x8000*8+2*4*16, 0*0x8000*8+2*4*16,
	      3*0x8000*8+3*4*16, 2*0x8000*8+3*4*16, 1*0x8000*8+3*4*16, 0*0x8000*8+3*4*16, },
	    16*16     /* every sprite takes 32 consecutive bytes */
	);
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, charlayout  , 0, 64 ),
		new GfxDecodeInfo( REGION_GFX2, 0, charlayout  , 0, 64 ),
		new GfxDecodeInfo( REGION_GFX3, 0, spritelayout, 0, 64 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	
	public static InterruptPtr sauron_interrupt = new InterruptPtr() { public int handler() 
	{
		cpu_cause_interrupt(1,Z80_NMI_INT);
		return -1000;	/* IRQ, why isn't there a constant defined? */
	} };
	
	static YM3526interface ym3812_interface = new YM3526interface
	(
		1,			/* 1 chip (no more supported) */
		3600000,	/* 3.600000 MHz ? */
		new int[]{ 255 } 	/* (not supported) */
        );
	
	
	static MachineDriver machine_driver_sauro = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				4000000,        /* 4 MHz??? */
				readmem,writemem,readport,writeport,
				interrupt,1
			),
			new MachineCPU(
				CPU_Z80 | CPU_AUDIO_CPU,
				4000000,        /* 4 MHz??? */
				sound_readmem,sound_writemem,null,null,
				sauron_interrupt,8 /* ?? */
			)
		},
		60, 5000,  /* frames per second, vblank duration (otherwise sprites lag) */
		1,      /* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 1*8, 31*8-1, 2*8, 30*8-1 ),
		gfxdecodeinfo,
		1024, 0,
		palette_RRRR_GGGG_BBBB_convert_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		generic_vh_start,
		generic_vh_stop,
		sauro_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM3812,
				ym3812_interface
			)
		}
	);
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	static RomLoadPtr rom_sauro = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );         /* 64k for code */
		ROM_LOAD( "sauro-2.bin",     0x00000, 0x8000, 0x19f8de25 );	ROM_LOAD( "sauro-1.bin",     0x08000, 0x8000, 0x0f8b876f );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );         /* 64k for sound CPU */
		ROM_LOAD( "sauro-3.bin",     0x00000, 0x8000, 0x0d501e1b );
		ROM_REGION( 0x10000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "sauro-4.bin",     0x00000, 0x8000, 0x9b617cda );	ROM_LOAD( "sauro-5.bin",     0x08000, 0x8000, 0xa6e2640d );
		ROM_REGION( 0x10000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "sauro-6.bin",     0x00000, 0x8000, 0x4b77cb0f );	ROM_LOAD( "sauro-7.bin",     0x08000, 0x8000, 0x187da060 );
		ROM_REGION( 0x20000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "sauro-8.bin",     0x00000, 0x8000, 0xe08b5d5e );	ROM_LOAD( "sauro-9.bin",     0x08000, 0x8000, 0x7c707195 );	ROM_LOAD( "sauro-10.bin",    0x10000, 0x8000, 0xc93380d1 );	ROM_LOAD( "sauro-11.bin",    0x18000, 0x8000, 0xf47982a8 );
		ROM_REGION( 0x0c00, REGION_PROMS, 0 );	ROM_LOAD( "82s137-3.bin",    0x0000, 0x0400, 0xd52c4cd0 ); /* Red component */
		ROM_LOAD( "82s137-2.bin",    0x0400, 0x0400, 0xc3e96d5d ); /* Green component */
		ROM_LOAD( "82s137-1.bin",    0x0800, 0x0400, 0xbdfcf00c ); /* Blue component */
	ROM_END(); }}; 
	
	
	
	public static InitDriverPtr init_sauro = new InitDriverPtr() { public void handler()
	{
		/* This game doesn't like all memory to be initialized to zero, it won't
		   initialize the high scores */
	
		UBytePtr RAM = new UBytePtr(memory_region(REGION_CPU1));
	
		memset(new UBytePtr(RAM, 0xe000), 0, 0x100);
		RAM.write(0xe000, 1);
	} };
	
	
	public static GameDriver driver_sauro	   = new GameDriver("1987"	,"sauro"	,"sauro.java"	,rom_sauro,null	,machine_driver_sauro	,input_ports_sauro	,init_sauro	,ROT0	,	"Tecfri", "Sauro", GAME_IMPERFECT_COLORS | GAME_IMPERFECT_SOUND );
}
