/***************************************************************************

Phozon (Namco 1983)

	Manuel Abadia (emumanu@hotmail.com)

Phozon Memory Map (preliminary)

CPU #1: (MAIN CPU)
0000-03ff   video RAM
0400-07ff   color RAM
0800-1fff   shared RAM with CPU #2
4040-43ff   shared RAM with CPU #3
4800-480f	custom IO chip #1
4810-481f	custom IO chip #2
5000-5009	???
500a-500b	CPU #3 enable
500c-500d	CPU #2 enable
500e-500f	???
7000		watchdog reset
8000-9fff	ROM
a000-bfff   ROM
c000-dfff   ROM
e000-ffff   ROM

CPU #2: (SUB CPU)
0000-03ff   video RAM (shared with CPU #1)
0400-07ff   color RAM (shared with CPU #1)
0800-1fff   shared RAM with CPU #1
a000-a7ff   RAM
e000-ffff   ROM

CPU #3: (SOUND CPU)
0000-0040   sound registers
0040-03ff   shared RAM with CPU #1
e000-ffff   ROM

TODO: cocktail mode

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP.mame056.machine.phozon.*;
import static WIP.mame056.vidhrdw.phozon.*;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;

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
import static WIP2.mame056.sound.namco.*;
import static WIP2.mame056.sound.namcoH.*;
import static WIP2.mame056.vidhrdw.generic.*;

import static WIP2.arcadeflex056.fileio.*;
import static mame056.palette.*;

public class phozon
{
	/* CPU 1 (MAIN CPU) read addresses */
	public static Memory_ReadAddress readmem_cpu1[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x03ff, videoram_r ),			/* video RAM */
		new Memory_ReadAddress( 0x0400, 0x07ff, colorram_r ),										/* color RAM */
		new Memory_ReadAddress( 0x0800, 0x1fff, phozon_spriteram_r ),			/* shared RAM with CPU #2/sprite RAM*/
		new Memory_ReadAddress( 0x4040, 0x43ff, phozon_snd_sharedram_r ),  /* shared RAM with CPU #3 */
		new Memory_ReadAddress( 0x4800, 0x480f, phozon_customio_1_r ),		/* custom I/O chip #1 interface */
		new Memory_ReadAddress( 0x4810, 0x481f, phozon_customio_2_r ),		/* custom I/O chip #2 interface */
		new Memory_ReadAddress( 0x8000, 0xffff, MRA_ROM ),										/* ROM */
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
		/* CPU 1 (MAIN CPU) write addresses */
	public static Memory_WriteAddress writemem_cpu1[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x03ff, videoram_w, videoram, videoram_size ),				/* video RAM */
		new Memory_WriteAddress( 0x0400, 0x07ff, colorram_w, colorram ),  /* color RAM */
		new Memory_WriteAddress( 0x0800, 0x1fff, phozon_spriteram_w, phozon_spriteram ),		/* shared RAM with CPU #2/sprite RAM*/
		new Memory_WriteAddress( 0x4000, 0x403f, MWA_RAM ),				/* initialized but probably unused */
		new Memory_WriteAddress( 0x4040, 0x43ff, phozon_snd_sharedram_w, phozon_snd_sharedram ), /* shared RAM with CPU #3 */
		new Memory_WriteAddress( 0x4800, 0x480f, phozon_customio_1_w, phozon_customio_1 ),	/* custom I/O chip #1 interface */
		new Memory_WriteAddress( 0x4810, 0x481f, phozon_customio_2_w, phozon_customio_2 ),	/* custom I/O chip #2 interface */
		new Memory_WriteAddress( 0x4820, 0x483f, MWA_RAM ),				/* initialized but probably unused */
		new Memory_WriteAddress( 0x5000, 0x5007, MWA_NOP ),				/* ??? */
		new Memory_WriteAddress( 0x5008, 0x5008, phozon_cpu3_reset_w ),	/* reset SOUND CPU? */
		new Memory_WriteAddress( 0x5009, 0x5009, MWA_NOP ),				/* ??? */
		new Memory_WriteAddress( 0x500a, 0x500b, phozon_cpu3_enable_w ),	/* SOUND CPU enable */
		new Memory_WriteAddress( 0x500c, 0x500d, phozon_cpu2_enable_w ),	/* SUB CPU enable */
		new Memory_WriteAddress( 0x500e, 0x500f, MWA_NOP ),				/* ??? */
		new Memory_WriteAddress( 0x7000, 0x7000, watchdog_reset_w ),	 	/* watchdog reset */
		new Memory_WriteAddress( 0x8000, 0xffff, MWA_ROM ),				/* ROM */
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
		/* CPU 2 (SUB CPU) read addresses */
	public static Memory_ReadAddress readmem_cpu2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x03ff, videoram_r ),			/* video RAM */
		new Memory_ReadAddress( 0x0400, 0x07ff, colorram_r ),			/* color RAM */
		new Memory_ReadAddress( 0x0800, 0x1fff, phozon_spriteram_r ),	/* shared RAM with CPU #1/sprite RAM*/
		new Memory_ReadAddress( 0xa000, 0xa7ff, MRA_RAM ),			/* RAM */
		new Memory_ReadAddress( 0xe000, 0xffff, MRA_ROM ),			/* ROM */
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
		/* CPU 2 (SUB CPU) write addresses */
	public static Memory_WriteAddress writemem_cpu2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x03ff, videoram_w ),			/* video RAM */
		new Memory_WriteAddress( 0x0400, 0x07ff, colorram_w ),			/* color RAM */
		new Memory_WriteAddress( 0x0800, 0x1fff, phozon_spriteram_w ),	/* shared RAM with CPU #1/sprite RAM*/
		new Memory_WriteAddress( 0xa000, 0xa7ff, MWA_RAM ),			/* RAM */
		new Memory_WriteAddress( 0xe000, 0xffff, MWA_ROM ),			/* ROM */
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
		/* CPU 3 (SOUND CPU) read addresses */
	public static Memory_ReadAddress readmem_cpu3[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x003f, MRA_RAM ),				/* sound registers */
		new Memory_ReadAddress( 0x0040, 0x03ff, phozon_snd_sharedram_r ), /* shared RAM with CPU #1 */
		new Memory_ReadAddress( 0xe000, 0xffff, MRA_ROM ),				/* ROM */
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
		/* CPU 3 (SOUND CPU) write addresses */
	public static Memory_WriteAddress writemem_cpu3[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	
                new Memory_WriteAddress( 0x0000, 0x003f, mappy_sound_w, namco_soundregs ),/* sound registers */
		new Memory_WriteAddress( 0x0040, 0x03ff, phozon_snd_sharedram_w ),			/* shared RAM with the main CPU */
		new Memory_WriteAddress( 0xe000, 0xffff, MWA_ROM ),						/* ROM */
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	/* The dipswitches and player inputs are not memory mapped, they are handled by an I/O chip. */
	static InputPortPtr input_ports_phozon = new InputPortPtr(){ public void handler() { 
	PORT_START();   /* DSW0 */
		PORT_DIPNAME( 0x07, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x07, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "1C_6C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_7C") );
		PORT_DIPNAME( 0x18, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x10, "1" );	PORT_DIPSETTING(    0x00, "3" );	PORT_DIPSETTING(    0x08, "4" );	PORT_DIPSETTING(    0x18, "5" );	PORT_DIPNAME( 0x60, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x60, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_2C") );
	
		PORT_START();   /* DSW1 */
		PORT_DIPNAME( 0x07, 0x00, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x00, "0" );	PORT_DIPSETTING(    0x01, "1" );	PORT_DIPSETTING(    0x02, "2" );	PORT_DIPSETTING(    0x03, "3" );	PORT_DIPSETTING(    0x04, "4" );	PORT_DIPSETTING(    0x05, "5" );	PORT_DIPSETTING(    0x06, "6" );	PORT_DIPSETTING(    0x07, "7" );	PORT_SERVICE( 0x08, IP_ACTIVE_HIGH );/* Todo: those are different for 4 and 5 lives */
		PORT_DIPNAME( 0xc0, 0x00, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0xc0, "20k 80k" );	PORT_DIPSETTING(    0x40, "30k 60k" );	PORT_DIPSETTING(    0x80, "30k 120k and every 120k" );	PORT_DIPSETTING(    0x00, "30k 100k" );
		PORT_START();   /* IN0 */
		PORT_BIT_IMPULSE(   0x01, IP_ACTIVE_HIGH, IPT_START1, 1 );	PORT_BIT_IMPULSE(   0x02, IP_ACTIVE_HIGH, IPT_START2, 1 );	PORT_BIT_IMPULSE(   0x10, IP_ACTIVE_HIGH, IPT_COIN1, 1 );	PORT_BIT_IMPULSE(   0x20, IP_ACTIVE_HIGH, IPT_COIN2, 1 );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_DIPNAME( 0x80, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Cocktail") );
	
		PORT_START();   /* IN1 */
		PORT_BIT(   0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP    | IPF_8WAY );	PORT_BIT(   0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_8WAY );	PORT_BIT(   0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN  | IPF_8WAY );	PORT_BIT(   0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT  | IPF_8WAY );	PORT_BIT(   0x10, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP    | IPF_8WAY | IPF_PLAYER2 );	PORT_BIT(   0x20, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_PLAYER2 );	PORT_BIT(   0x40, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN  | IPF_8WAY | IPF_PLAYER2 );	PORT_BIT(   0x80, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT  | IPF_8WAY | IPF_PLAYER2 );
		PORT_START();   /* IN2 */
		PORT_BIT_IMPULSE( 0x01, IP_ACTIVE_HIGH, IPT_BUTTON1, 1 );	
                PORT_BITX(  0x02, IP_ACTIVE_HIGH, IPT_BUTTON1, null, IP_KEY_PREVIOUS, IP_JOY_PREVIOUS );	
                PORT_BIT_IMPULSE( 0x04, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_PLAYER2, 1 );	
                PORT_BITX(  0x08, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_PLAYER2, null, IP_KEY_PREVIOUS, IP_JOY_PREVIOUS );INPUT_PORTS_END(); }}; 
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,            /* 8*8 characters */
		256,            /* 256 characters */
		2,				/* 2 bits per pixel */
		new int[] { 0, 4 },
		new int[] { 8*8+0, 8*8+1, 8*8+2, 8*8+3, 0, 1, 2, 3 },   /* bits are packed in groups of four */
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },   /* characters are rotated 90 degrees */
		16*8			/* every char takes 16 bytes */
	);
	
	static GfxLayout spritelayout = new GfxLayout
	(
		16,16,                                         /* 16*16 sprites */
		128,                                           /* 128 sprites */
		2,                                             /* 2 bits per pixel */
		new int[] { 0, 4 },
		new int[] { 0, 1, 2, 3, 8*8, 8*8+1, 8*8+2, 8*8+3,
			16*8+0, 16*8+1, 16*8+2, 16*8+3, 24*8+0, 24*8+1, 24*8+2, 24*8+3 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8,
			32*8, 33*8, 34*8, 35*8, 36*8, 37*8, 38*8, 39*8 },
		64*8                                           /* every sprite takes 64 bytes */
	);
	
	static GfxLayout spritelayout8 = new GfxLayout
	(
		8,8,                                         /* 16*16 sprites */
		512,                                           /* 128 sprites */
		2,                                             /* 2 bits per pixel */
		new int[] { 0, 4 },
		new int[] { 0, 1, 2, 3, 8*8, 8*8+1, 8*8+2, 8*8+3 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		16*8                                           /* every sprite takes 64 bytes */
	);
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, charlayout,       0, 64 ),
		new GfxDecodeInfo( REGION_GFX2, 0, charlayout,       0, 64 ),
		new GfxDecodeInfo( REGION_GFX3, 0, spritelayout,  64*4, 64 ),
		new GfxDecodeInfo( REGION_GFX3, 0, spritelayout8, 64*4, 64 ),
		new GfxDecodeInfo( -1 ) /* end of table */
	};
	
	static namco_interface namco_interface = new namco_interface
	(
		24000,	/* sample rate */
		8,		/* number of voices */
		100,	/* playback volume */
		REGION_SOUND1	/* memory region */
        );
	
	static MachineDriver machine_driver_phozon = new MachineDriver
	(
		/* basic machine hardware  */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6809,			/* MAIN CPU */
				1536000,			/* same as Gaplus? */
				readmem_cpu1,writemem_cpu1,null,null,
				interrupt,1
			),
			new MachineCPU(
				CPU_M6809,			/* SUB CPU */
				1536000,			/* same as Gaplus? */
				readmem_cpu2,writemem_cpu2,null,null,
				interrupt,1
			),
			new MachineCPU(
				CPU_M6809,			/* SOUND CPU */
				1536000,			/* same as Gaplus? */
				readmem_cpu3,writemem_cpu3,null,null,
				interrupt,1
			),
		},
		60.606060f, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		100,	/* a high value to ensure proper synchronization of the CPUs */
		phozon_init_machine,	/* init machine routine */
	
		/* video hardware */
		36*8, 28*8, new rectangle( 0*8, 36*8-1, 0*8, 28*8-1 ),
		gfxdecodeinfo,
		256,
		64*4+64*8,
		phozon_vh_convert_color_prom,
		VIDEO_TYPE_RASTER,
		null,
		phozon_vh_start,
		phozon_vh_stop,
		phozon_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_NAMCO,
				namco_interface
			)
		}
	);
	
	
	
	static RomLoadPtr rom_phozon = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );    /* 64k for code for the MAIN CPU  */
		ROM_LOAD( "6e.rom", 0x8000, 0x2000, 0xa6686af1 );	ROM_LOAD( "6h.rom", 0xa000, 0x2000, 0x72a65ba0 );	ROM_LOAD( "6c.rom", 0xc000, 0x2000, 0xf1fda22e );	ROM_LOAD( "6d.rom", 0xe000, 0x2000, 0xf40e6df0 );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );    /* 64k for the SUB CPU */
		ROM_LOAD( "9r.rom", 0xe000, 0x2000, 0x5d9f0a28 );
		ROM_REGION( 0x10000, REGION_CPU3, 0 );    /* 64k for the SOUND CPU */
		ROM_LOAD( "3b.rom", 0xe000, 0x2000, 0x5a4b3a79 );
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "7j.rom", 0x0000, 0x1000, 0x27f9db5b );/* characters (set 1) */
	
		ROM_REGION( 0x1000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "8j.rom", 0x0000, 0x1000, 0x15b12ef8 );/* characters (set 2) */
	
		ROM_REGION( 0x2000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "5t.rom", 0x0000, 0x2000, 0xd50f08f8 );/* sprites */
	
		ROM_REGION( 0x0520, REGION_PROMS, 0 );	ROM_LOAD( "red.prm",     0x0000, 0x0100, 0xa2880667 );/* red palette ROM (4 bits) */
		ROM_LOAD( "green.prm",   0x0100, 0x0100, 0xd6e08bef );/* green palette ROM (4 bits) */
		ROM_LOAD( "blue.prm",    0x0200, 0x0100, 0xb2d69c72 );/* blue palette ROM (4 bits) */
		ROM_LOAD( "chr.prm",     0x0300, 0x0100, 0x429e8fee );/* characters */
		ROM_LOAD( "sprite.prm",  0x0400, 0x0100, 0x9061db07 );/* sprites */
		ROM_LOAD( "palette.prm", 0x0500, 0x0020, 0x60e856ed );/* palette (unused?) */
	
		ROM_REGION( 0x0100, REGION_SOUND1, 0 );/* sound PROMs */
		ROM_LOAD( "sound.prm", 0x0000, 0x0100, 0xad43688f );ROM_END(); }}; 
	
	
	
	public static GameDriver driver_phozon	   = new GameDriver("1983"	,"phozon"	,"phozon.java"	,rom_phozon,null	,machine_driver_phozon	,input_ports_phozon	,null	,ROT90	,	"Namco", "Phozon", GAME_NO_COCKTAIL );
}
