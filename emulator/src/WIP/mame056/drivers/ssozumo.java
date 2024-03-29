/***************************************************************************

Syusse Oozumou
(c) 1984 Technos Japan (Licensed by Data East)

Driver by Takahiro Nogi (nogi@kt.rim.or.jp) 1999/10/04

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP.mame056.vidhrdw.ssozumo.*;

import static WIP2.arcadeflex056.fucPtr.*;
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
import static WIP2.mame056.inputH.*;
import static WIP2.mame056.sndintrfH.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sound.samples.*;
import static WIP2.mame056.sound.samplesH.*;
import static WIP2.mame056.sound.mixer.*;
import static WIP2.mame056.sound.mixerH.*;
import static WIP2.mame056.sound.dac.*;
import static WIP2.mame056.sound.dacH.*;
import static WIP2.mame056.sound.ay8910.*;
import static WIP2.mame056.sound.ay8910H.*;
import static WIP2.mame056.sound.pokey.*;
import static WIP2.mame056.sound.pokeyH.*;

import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.mame056.timer.*;
import static WIP2.mame056.timerH.*;

import static WIP2.arcadeflex056.fileio.*;
import static WIP2.mame056.palette.*;
// refactor
import static WIP2.arcadeflex036.osdepend.logerror;

public class ssozumo
{
	static int coin;
	
	public static InterruptPtr ssozumo_interrupt = new InterruptPtr() { public int handler() 
	{
		
		if ((readinputport(0) & 0xc0) != 0xc0)
		{
			if (coin == 0)
			{
				coin = 1;
				return nmi_interrupt.handler();
			}
		}
		else coin = 0;
	
		return interrupt.handler();
	} };
	
	
	public static WriteHandlerPtr ssozumo_sh_command_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		soundlatch_w.handler(offset, data);
		cpu_cause_interrupt(1, M6502_INT_IRQ);
	} };
	
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x077f, MRA_RAM ),
	
		new Memory_ReadAddress( 0x2000, 0x27ff, MRA_RAM ),
		new Memory_ReadAddress( 0x3000, 0x31ff, MRA_RAM ),
	
		new Memory_ReadAddress( 0x4000, 0x4000, input_port_0_r ),
		new Memory_ReadAddress( 0x4010, 0x4010, input_port_1_r ),
		new Memory_ReadAddress( 0x4020, 0x4020, input_port_2_r ),
		new Memory_ReadAddress( 0x4030, 0x4030, input_port_3_r ),
	
		new Memory_ReadAddress( 0x6000, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x077f, MWA_RAM ),
	
		new Memory_WriteAddress( 0x0780, 0x07ff, MWA_RAM, spriteram, spriteram_size ),
		new Memory_WriteAddress( 0x2000, 0x23ff, MWA_RAM, ssozumo_videoram2, ssozumo_videoram2_size ),
		new Memory_WriteAddress( 0x2400, 0x27ff, MWA_RAM, ssozumo_colorram2 ),
		new Memory_WriteAddress( 0x3000, 0x31ff, videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0x3200, 0x33ff, colorram_w, colorram ),
		new Memory_WriteAddress( 0x3400, 0x35ff, MWA_RAM ),
		new Memory_WriteAddress( 0x3600, 0x37ff, MWA_RAM ),
	
		new Memory_WriteAddress( 0x4000, 0x4000, MWA_RAM ),			// fg page select?
		new Memory_WriteAddress( 0x4010, 0x4010, ssozumo_sh_command_w ),
		new Memory_WriteAddress( 0x4020, 0x4020, MWA_RAM, ssozumo_scroll ),
	//	new Memory_WriteAddress( 0x4030, 0x4030, MWA_RAM ),
		new Memory_WriteAddress( 0x4050, 0x407f, ssozumo_paletteram_w, paletteram ),
	
		new Memory_WriteAddress( 0x6000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	public static Memory_ReadAddress sound_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x01ff, MRA_RAM ),
		new Memory_ReadAddress( 0x2007, 0x2007, soundlatch_r ),
		new Memory_ReadAddress( 0x4000, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	
	public static Memory_WriteAddress sound_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x01ff, MWA_RAM ),
		new Memory_WriteAddress( 0x2000, 0x2000, AY8910_write_port_0_w ),
		new Memory_WriteAddress( 0x2001, 0x2001, AY8910_control_port_0_w ),
		new Memory_WriteAddress( 0x2002, 0x2002, AY8910_write_port_1_w ),
		new Memory_WriteAddress( 0x2003, 0x2003, AY8910_control_port_1_w ),
		new Memory_WriteAddress( 0x2004, 0x2004, DAC_0_signed_data_w ),
		new Memory_WriteAddress( 0x2005, 0x2005, interrupt_enable_w ),
		new Memory_WriteAddress( 0x4000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	static InputPortPtr input_ports_ssozumo = new InputPortPtr(){ public void handler() { 
		PORT_START(); 	/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP    | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_COIN1 );
	
		PORT_START(); 	/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP    | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START2 );
	
		PORT_START();       /* DSW2 */
		PORT_DIPNAME( 0x01, 0x01, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x01, "Normal" );
		PORT_DIPSETTING(    0x00, "Hard" );
		PORT_DIPNAME( 0x02, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x04, 0x04, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x04, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x10, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x20, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x20, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_3C") );
		PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x0c, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x08, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_3C") );
		PORT_DIPNAME( 0x10, 0x10, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	//#if 0
	//	PORT_DIPNAME( 0x20, 0x20, DEF_STR( "Flip_Screen") );
	//	PORT_DIPSETTING(    0x20, DEF_STR( "Off") );
	//	PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	//#endif
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Cocktail") );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_VBLANK );
	INPUT_PORTS_END(); }}; 
	
	
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,		/* 8*8 characters */
		1024,		/* 1024 characters */
		3,		/* 3 bits per pixel */
		new int[] { 2*1024*8*8, 1024*8*8, 0 },	/* the bitplanes are separated */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		8*8		/* every char takes 8 consecutive bytes */
	);
	
	
	static GfxLayout tilelayout = new GfxLayout
	(
		16,16,	/* 16*16 tiles */
		256,	/* 256 tiles */
		3,		/* 3 bits per pixel */
		new int[] { 2*256*16*16, 256*16*16, 0 },	/* the bitplanes are separated */
		new int[] { 16*8 + 0, 16*8 + 1, 16*8 + 2, 16*8 + 3, 16*8 + 4, 16*8 + 5, 16*8 + 6, 16*8 + 7,
				0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8,
				8*8, 9*8, 10*8, 11*8, 12*8, 13*8, 14*8, 15*8 },
		32*8		/* every tile takes 16 consecutive bytes */
	);
	
	
	static GfxLayout spritelayout = new GfxLayout
	(
		16,16,		/* 16*16 sprites */
		1280,		/* 1280 sprites */
		3,		/* 3 bits per pixel */
		new int[] { 2*1280*16*16, 1280*16*16, 0 },	/* the bitplanes are separated */
		new int[] { 16*8 + 0, 16*8 + 1, 16*8 + 2, 16*8 + 3, 16*8 + 4, 16*8 + 5, 16*8 + 6, 16*8 + 7,
				0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8,
				8*8, 9*8, 10*8, 11*8, 12*8, 13*8, 14*8, 15*8 },
		32*8		/* every sprite takes 16 consecutive bytes */
	);
	
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, charlayout,     0, 4 ),
		new GfxDecodeInfo( REGION_GFX2, 0, tilelayout,   4*8, 4 ),
		new GfxDecodeInfo( REGION_GFX3, 0, spritelayout, 8*8, 2 ),
		new GfxDecodeInfo( -1 )		/* end of array */
	};
	
	
	static AY8910interface ay8910_interface = new AY8910interface
	(
		2,		/* 2 chips */
		1500000,	/* 1.5 MHz?????? */
		new int[] { 30, 30 },
		new ReadHandlerPtr[] { null, null },
		new ReadHandlerPtr[] { null, null },
		new WriteHandlerPtr[] { null, null },
		new WriteHandlerPtr[] { null, null }
	);
	
	
	static DACinterface dac_interface = new DACinterface
	(
		1,
		new int[] { 30 }
	);
	
	
	static MachineDriver machine_driver_ssozumo = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6502,
				1200000,	/* 1.2 MHz ???? */
				readmem, writemem, null, null,
				ssozumo_interrupt, 1
			),
			new MachineCPU(
				CPU_M6502 | CPU_AUDIO_CPU,
				975000, 		/* 975 kHz ?? */
				sound_readmem, sound_writemem, null, null,
				nmi_interrupt,16	/* IRQs are triggered by the main CPU */
			)
		},
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,					/* 1 CPU slice per frame - interleaving is forced when a sound command is written */
		null,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8 - 1, 1*8, 31*8 - 1 ),
		gfxdecodeinfo,
		64 + 16, 64 + 16,
		ssozumo_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		ssozumo_vh_start,
		ssozumo_vh_stop,
		ssozumo_vh_screenrefresh,
	
		/* sound hardware */
		0, 0, 0, 0,
		new MachineSound[] {
			new MachineSound(
				SOUND_AY8910,
				ay8910_interface
			),
			new MachineSound(
				SOUND_DAC,
				dac_interface
			)
		}
	);
	
	
	
	static RomLoadPtr rom_ssozumo = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );
		/* Main Program ROMs */
		ROM_LOAD( "ic61.g01",	0x06000, 0x2000, 0x86968f46 );// m1
		ROM_LOAD( "ic60.g11",	0x08000, 0x2000, 0x1a5143dd );// m2
		ROM_LOAD( "ic59.g21",	0x0a000, 0x2000, 0xd3df04d7 );// m3
		ROM_LOAD( "ic58.g31",	0x0c000, 0x2000, 0x0ee43a78 );// m4
		ROM_LOAD( "ic57.g41",	0x0e000, 0x2000, 0xac77aa4c );// m5
	
		ROM_REGION( 0x10000, REGION_CPU2, 0 );
		/* Sound Program & Voice Sample ROMs*/
		ROM_LOAD( "ic47.g50",	0x04000, 0x2000, 0xb64ec829 );// a1
		ROM_LOAD( "ic46.g60",	0x06000, 0x2000, 0x630d7380 );// a2
		ROM_LOAD( "ic45.g70",	0x08000, 0x2000, 0x1854b657 );// a3
		ROM_LOAD( "ic44.g80",	0x0a000, 0x2000, 0x40b9a0da );// a4
		ROM_LOAD( "ic43.g90",	0x0c000, 0x2000, 0x20262064 );// a5
		ROM_LOAD( "ic42.ga0",	0x0e000, 0x2000, 0x98d7e998 );// a6
	
		ROM_REGION( 0x06000, REGION_GFX1, ROMREGION_DISPOSE );
		/* Character ROMs */
		ROM_LOAD( "ic22.gq0",	0x00000, 0x2000, 0xb4c7e612 );// c1
		ROM_LOAD( "ic23.gr0",	0x02000, 0x2000, 0x90bb9fda );// c2
		ROM_LOAD( "ic21.gs0",	0x04000, 0x2000, 0xd8cd5c78 );// c3
	
		ROM_REGION( 0x06000, REGION_GFX2, ROMREGION_DISPOSE );
		/* tile set ROMs */
		ROM_LOAD( "ic69.gt0",	0x00000, 0x2000, 0x771116ca );// t1
		ROM_LOAD( "ic59.gu0",	0x02000, 0x2000, 0x68035bfd );// t2
		ROM_LOAD( "ic81.gv0",	0x04000, 0x2000, 0xcdda1f9f );// t3
	
		ROM_REGION( 0x1e000, REGION_GFX3, ROMREGION_DISPOSE );
		/* sprites ROMs */
		ROM_LOAD( "ic06.gg0",	0x00000, 0x2000, 0xd2342c50 );// s1a
		ROM_LOAD( "ic05.gh0",	0x02000, 0x2000, 0x14a3cb10 );// s1b
		ROM_LOAD( "ic04.gi0",	0x04000, 0x2000, 0x169276c1 );// s1c
		ROM_LOAD( "ic03.gj0",	0x06000, 0x2000, 0xe71b9f28 );// s1d
		ROM_LOAD( "ic02.gk0",	0x08000, 0x2000, 0x6e94773c );// s1e
		ROM_LOAD( "ic29.gl0",	0x0a000, 0x2000, 0x40f67cc4 );// s2a
		ROM_LOAD( "ic28.gm0",	0x0c000, 0x2000, 0x8c97b1a2 );// s2b
		ROM_LOAD( "ic27.gn0",	0x0e000, 0x2000, 0xbe8bb3dd );// s2c
		ROM_LOAD( "ic26.go0",	0x10000, 0x2000, 0x9c098a2c );// s2d
		ROM_LOAD( "ic25.gp0",	0x12000, 0x2000, 0xf73f8a76 );// s2e
		ROM_LOAD( "ic44.gb0",	0x14000, 0x2000, 0xcdd7f2eb );// s3a
		ROM_LOAD( "ic43.gc0",	0x16000, 0x2000, 0x7b4c632e );// s3b
		ROM_LOAD( "ic42.gd0",	0x18000, 0x2000, 0xcd1c8fe6 );// s3c
		ROM_LOAD( "ic41.ge0",	0x1a000, 0x2000, 0x935578d0 );// s3d
		ROM_LOAD( "ic40.gf0",	0x1c000, 0x2000, 0x5a3bf1ba );// s3e
	
		ROM_REGION( 0x0080, REGION_PROMS, 0 );
		ROM_LOAD( "ic33.gz0",	0x00000, 0x0020, 0x523d29ad );/* char palette red and green components */
		ROM_LOAD( "ic30.gz2",	0x00020, 0x0020, 0x0de202e1 );/* tile palette red and green components */
		ROM_LOAD( "ic32.gz1",	0x00040, 0x0020, 0x6fbff4d2 );/* char palette blue component */
		ROM_LOAD( "ic31.gz3",	0x00060, 0x0020, 0x18e7fe63 );/* tile palette blue component */
	ROM_END(); }}; 
	
	
	
	public static GameDriver driver_ssozumo	   = new GameDriver("1984"	,"ssozumo"	,"ssozumo.java"	,rom_ssozumo,null	,machine_driver_ssozumo	,input_ports_ssozumo	,null	,ROT270	,	"Technos", "Syusse Oozumou (Japan)", GAME_NO_COCKTAIL );
}
