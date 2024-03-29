/***************************************************************************

	Pocket Gal						(c) 1987 Data East Corporation
	Pocket Gal (Bootleg)			(c) 1989 Yada East Corporation(!!!)
	Super Pool III					(c) 1989 Data East Corporation
	Pocket Gal 2					(c) 1989 Data East Corporation
	Super Pool III (I-Vics Inc)		(c) 1990 Data East Corporation

	Pocket Gal (Bootleg) is often called 'Sexy Billiards'

	Emulation by Bryan McPhail, mish@tendril.co.uk

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;

import static WIP2.mame056.common.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.memory.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.inputH.*;
import static WIP2.mame056.sndintrfH.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sound.samples.*;
import static WIP2.mame056.sound.samplesH.*;
import static WIP2.mame056.sound.dac.*;
import static WIP2.mame056.sound.dacH.*;
// refactor
import static WIP2.arcadeflex036.osdepend.logerror;

import static WIP2.mame056.vidhrdw.generic.*;

import static WIP2.arcadeflex056.fileio.*;
import static mame056.palette.*;
import static WIP2.mame056.inptport.*;

import static WIP.mame056.vidhrdw.pcktgal.*;
import static WIP2.common.libc.cstring.memcpy;
import static WIP2.mame056.sound.MSM5205.*;
import static WIP2.mame056.sound.MSM5205H.*;
import static WIP2.mame056.sound._2203intf.*;
import static WIP2.mame056.sound._2203intfH.*;
import static WIP2.mame056.sound._3812intf.*;
import static WIP2.mame056.sound._3812intfH.*;

public class pcktgal
{
	
	/***************************************************************************/
	
	public static WriteHandlerPtr pcktgal_bank_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		UBytePtr RAM = new UBytePtr(memory_region(REGION_CPU1));
	
		if ((data & 1)!=0) { cpu_setbank(1,new UBytePtr(RAM, 0x4000)); }
		else { cpu_setbank(1,new UBytePtr(RAM, 0x10000)); }
	
		if ((data & 2)!=0) { cpu_setbank(2, new UBytePtr(RAM, 0x6000)); }
		else { cpu_setbank(2,new UBytePtr(RAM, 0x12000)); }
	} };
	
	public static WriteHandlerPtr pcktgal_sound_bank_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		UBytePtr RAM = new UBytePtr(memory_region(REGION_CPU2));
	
		if ((data & 4)!=0) { cpu_setbank(3,new UBytePtr(RAM, 0x14000)); }
		else { cpu_setbank(3, new UBytePtr(RAM, 0x10000)); }
	} };
	
	public static WriteHandlerPtr pcktgal_sound_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		soundlatch_w.handler(0,data);
		cpu_cause_interrupt(1,M6502_INT_NMI);
	} };
	
	static int msm5205next;
        static int toggle;
	
	static vclk_interruptPtr pcktgal_adpcm_int = new vclk_interruptPtr() {
            public void handler(int data) {
                MSM5205_data_w.handler(0,msm5205next >> 4);
		msm5205next<<=4;
	
		toggle = 1 - toggle;
		if (toggle != 0)
			cpu_cause_interrupt(1,M6502_INT_IRQ);
            }
        };
	
	public static WriteHandlerPtr pcktgal_adpcm_data_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		msm5205next=data;
	} };
	
	public static ReadHandlerPtr pcktgal_adpcm_reset_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		MSM5205_reset_w.handler(0,0);
		return 0;
	} };
	
	/***************************************************************************/
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x07ff, MRA_RAM ),
		new Memory_ReadAddress( 0x1800, 0x1800, input_port_0_r ),
		new Memory_ReadAddress( 0x1a00, 0x1a00, input_port_1_r ),
		new Memory_ReadAddress( 0x1c00, 0x1c00, input_port_2_r ),
		new Memory_ReadAddress( 0x4000, 0x5fff, MRA_BANK1 ),
		new Memory_ReadAddress( 0x6000, 0x7fff, MRA_BANK2 ),
		new Memory_ReadAddress( 0x8000, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x07ff, MWA_RAM ),
		new Memory_WriteAddress( 0x0800, 0x0fff, videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0x1000, 0x11ff, MWA_RAM, spriteram, spriteram_size ),
		new Memory_WriteAddress( 0x1801, 0x1801, pcktgal_flipscreen_w ),
		/* 1800 - 0x181f are unused BAC-06 registers, see vidhrdw/dec0.c */
		new Memory_WriteAddress( 0x1a00, 0x1a00, pcktgal_sound_w ),
		new Memory_WriteAddress( 0x1c00, 0x1c00, pcktgal_bank_w ),
		new Memory_WriteAddress( 0x4000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	/***************************************************************************/
	
	public static Memory_ReadAddress sound_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x07ff, MRA_RAM ),
		new Memory_ReadAddress( 0x3000, 0x3000, soundlatch_r ),
		new Memory_ReadAddress( 0x3400, 0x3400, pcktgal_adpcm_reset_r ),	/* ? not sure */
		new Memory_ReadAddress( 0x4000, 0x7fff, MRA_BANK3 ),
		new Memory_ReadAddress( 0x8000, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress sound_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x07ff, MWA_RAM ),
		new Memory_WriteAddress( 0x0800, 0x0800, YM2203_control_port_0_w ),
		new Memory_WriteAddress( 0x0801, 0x0801, YM2203_write_port_0_w ),
		new Memory_WriteAddress( 0x1000, 0x1000, YM3812_control_port_0_w ),
		new Memory_WriteAddress( 0x1001, 0x1001, YM3812_write_port_0_w ),
		new Memory_WriteAddress( 0x1800, 0x1800, pcktgal_adpcm_data_w ),	/* ADPCM data for the MSM5205 chip */
		new Memory_WriteAddress( 0x2000, 0x2000, pcktgal_sound_bank_w ),
		new Memory_WriteAddress( 0x4000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	/***************************************************************************/
	
	static InputPortPtr input_ports_pcktgal = new InputPortPtr(){ public void handler() { 
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_BUTTON1 );
	
		PORT_START(); 
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_PLAYER2 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_PLAYER2 );
	
		PORT_START(); 	/* Dip switch */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Coinage") );
		PORT_DIPSETTING(	0x00, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(	0x03, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(	0x02, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(	0x01, DEF_STR( "1C_3C") );
	 	PORT_DIPNAME( 0x04, 0x04, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(	0x04, DEF_STR( "Off") );
		PORT_DIPSETTING(	0x00, DEF_STR( "On") );
	 	PORT_DIPNAME( 0x08, 0x08, "Allow 2 Players Game" );
		PORT_DIPSETTING(	0x00, DEF_STR( "No") );
		PORT_DIPSETTING(	0x08, DEF_STR( "Yes") );
	 	PORT_DIPNAME( 0x10, 0x10, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(	0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(	0x10, DEF_STR( "On") );
	 	PORT_DIPNAME( 0x20, 0x20, "Time" );
		PORT_DIPSETTING(	0x00, "100" );
		PORT_DIPSETTING(	0x20, "120" );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(	0x00, "3" );
		PORT_DIPSETTING(	0x40, "4" );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(	0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(	0x00, DEF_STR( "On") );
	INPUT_PORTS_END(); }}; 
	
	/***************************************************************************/
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,	/* 8*8 characters */
		4096,
		4,
		new int[] { 0x10000*8, 0, 0x18000*8, 0x8000*8 },
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		8*8	 /* every char takes 8 consecutive bytes */
	);
	
	static GfxLayout bootleg_charlayout = new GfxLayout
	(
		8,8,	/* 8*8 characters */
		4096,
		4,
		new int[] { 0x18000*8, 0x8000*8, 0x10000*8, 0 },
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		8*8	 /* every char takes 8 consecutive bytes */
	);
	
	static GfxLayout spritelayout = new GfxLayout
	(
		16,16,  /* 16*16 sprites */
		1024,   /* 1024 sprites */
		2,	  /* 2 bits per pixel */
		new int[] { 0x8000*8, 0 },
		new int[] { 128+0, 128+1, 128+2, 128+3, 128+4, 128+5, 128+6, 128+7, 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8, 8*8, 9*8, 10*8, 11*8, 12*8, 13*8, 14*8, 15*8 },
		32*8	/* every char takes 8 consecutive bytes */
	);
	
	static GfxLayout bootleg_spritelayout = new GfxLayout
	(
		16,16,  /* 16*16 sprites */
		1024,   /* 1024 sprites */
		2,	  /* 2 bits per pixel */
		new int[] { 0x8000*8, 0 },
		new int[] { 128+7, 128+6, 128+5, 128+4, 128+3, 128+2, 128+1, 128+0, 7, 6, 5, 4, 3, 2, 1, 0,  },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8, 8*8, 9*8, 10*8, 11*8, 12*8, 13*8, 14*8, 15*8 },
		32*8	/* every char takes 8 consecutive bytes */
	);
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0x00000, charlayout,   256, 16 ), /* chars */
		new GfxDecodeInfo( REGION_GFX2, 0x00000, spritelayout,   0,  8 ), /* sprites */
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	static GfxDecodeInfo bootleg_gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0x00000, bootleg_charlayout,   256, 16 ), /* chars */
		new GfxDecodeInfo( REGION_GFX2, 0x00000, bootleg_spritelayout,   0,  8 ), /* sprites */
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	/***************************************************************************/
	
	static YM2203interface ym2203_interface = new YM2203interface
	(
		1,	  /* 1 chip */
		1500000,		/* 1.5 MHz */
		new int[]{ YM2203_VOL(60,60) },
		new ReadHandlerPtr[]{ null },
		new ReadHandlerPtr[]{ null },
		new WriteHandlerPtr[]{ null },
		new WriteHandlerPtr[]{ null }
	);
	
	static YM3812interface ym3812_interface = new YM3812interface
	(
		1,			/* 1 chip (no more supported) */
		3000000,	/* 3 MHz */
		new int[]{ 50 }
	);
	
	static MSM5205interface msm5205_interface = new MSM5205interface
        (
		1,					/* 1 chip			 */
		384000,				/* 384KHz			 */
		new vclk_interruptPtr[]{ pcktgal_adpcm_int },/* interrupt function */
		new int[]{ MSM5205_S48_4B},	/* 8KHz			   */
		new int[]{ 70 }
	);
	
	/***************************************************************************/
	
	static MachineDriver machine_driver_pcktgal = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6502,
				2000000,
				readmem,writemem,null,null,
				nmi_interrupt,1
			),
			new MachineCPU(
				CPU_M6502 | CPU_AUDIO_CPU,
				1500000,
				sound_readmem,sound_writemem,null,null,
				ignore_interrupt,0
								/* IRQs are caused by the ADPCM chip */
								/* NMIs are caused by the main CPU */
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,  /* frames per second, vblank duration */
		1, /* 1 CPU slice per frame - interleaving is forced when a sound command is written */
		null,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 30*8-1 ),
		gfxdecodeinfo,
		512, 0,
		pcktgal_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		generic_vh_start,
		generic_vh_stop,
		pcktgal_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2203,
				ym2203_interface
			),
			new MachineSound(
				SOUND_YM3812,
				ym3812_interface
			),
			new MachineSound(
				SOUND_MSM5205,
				msm5205_interface
			)
		}
	);
	
	static MachineDriver machine_driver_bootleg = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6502,
				2000000,
				readmem,writemem,null,null,
				nmi_interrupt,1
			),
			new MachineCPU(
				CPU_M6502 | CPU_AUDIO_CPU,
				1500000,
				sound_readmem,sound_writemem,null,null,
				ignore_interrupt,0
								/* IRQs are caused by the ADPCM chip */
								/* NMIs are caused by the main CPU */
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,  /* frames per second, vblank duration */
		1, /* 1 CPU slice per frame - interleaving is forced when a sound command is written */
		null,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 30*8-1 ),
		bootleg_gfxdecodeinfo,
		512, 0,
		pcktgal_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		generic_vh_start,
		generic_vh_stop,
		pcktgal_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2203,
				ym2203_interface
			),
			new MachineSound(
				SOUND_YM3812,
				ym3812_interface
			),
			new MachineSound(
				SOUND_MSM5205,
				msm5205_interface
			)
		}
	);
	
	/***************************************************************************/
	
	static RomLoadPtr rom_pcktgal = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x14000, REGION_CPU1, 0 ); /* 64k for code + 16k for banks */
		ROM_LOAD( "eb04.rom",	   0x10000, 0x4000, 0x8215d60d );
		ROM_CONTINUE(			   0x04000, 0xc000);
		/* 4000-7fff is banked but code falls through from 7fff to 8000, so */
		/* I have to load the bank directly at 4000. */
	
		ROM_REGION( 2*0x18000, REGION_CPU2, 0 ); /* 96k for code + 96k for decrypted opcodes */
		ROM_LOAD( "eb03.rom",	   0x10000, 0x8000, 0xcb029b02 );
		ROM_CONTINUE(			   0x08000, 0x8000 );
	
		ROM_REGION( 0x20000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "eb01.rom",	   0x00000, 0x10000, 0x63542c3d );
		ROM_LOAD( "eb02.rom",	   0x10000, 0x10000, 0xa9dcd339 );
	
		ROM_REGION( 0x10000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "eb00.rom",	   0x00000, 0x10000, 0x6c1a14a8 );
	
		ROM_REGION( 0x0400, REGION_PROMS, 0 );
		ROM_LOAD( "eb05.rom",     0x0000, 0x0200, 0x3b6198cb );/* 82s147.084 */
		ROM_LOAD( "eb06.rom",     0x0200, 0x0200, 0x1fbd4b59 );/* 82s131.101 */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_pcktgalb = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x14000, REGION_CPU1, 0 ); /* 64k for code + 16k for banks */
		ROM_LOAD( "sexybill.001", 0x10000, 0x4000, 0x4acb3e84 );
		ROM_CONTINUE(			  0x04000, 0xc000);
		/* 4000-7fff is banked but code falls through from 7fff to 8000, so */
		/* I have to load the bank directly at 4000. */
	
		ROM_REGION( 2*0x18000, REGION_CPU2, 0 ); /* 96k for code + 96k for decrypted opcodes */
		ROM_LOAD( "eb03.rom",	  0x10000, 0x8000, 0xcb029b02 );
		ROM_CONTINUE(			  0x08000, 0x8000 );
	
		ROM_REGION( 0x20000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "sexybill.005", 0x00000, 0x10000, 0x3128dc7b );
		ROM_LOAD( "sexybill.006", 0x10000, 0x10000, 0x0fc91eeb );
	
		ROM_REGION( 0x10000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "sexybill.003", 0x00000, 0x08000, 0x58182daa );
		ROM_LOAD( "sexybill.004", 0x08000, 0x08000, 0x33a67af6 );
	
		ROM_REGION( 0x0400, REGION_PROMS, 0 );
		ROM_LOAD( "eb05.rom",     0x0000, 0x0200, 0x3b6198cb );/* 82s147.084 */
		ROM_LOAD( "eb06.rom",     0x0200, 0x0200, 0x1fbd4b59 );/* 82s131.101 */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_pcktgal2 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x14000, REGION_CPU1, 0 ); /* 64k for code + 16k for banks */
		ROM_LOAD( "eb04-2.rom",   0x10000, 0x4000, 0x0c7f2905 );
		ROM_CONTINUE(			  0x04000, 0xc000);
		/* 4000-7fff is banked but code falls through from 7fff to 8000, so */
		/* I have to load the bank directly at 4000. */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 ); /* audio cpu */
		ROM_LOAD( "eb03-2.rom",   0x10000, 0x8000, 0x9408ffb4 );
		ROM_CONTINUE(			  0x08000, 0x8000);
	
		ROM_REGION( 0x20000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "eb01-2.rom",   0x00000, 0x10000, 0xe52b1f97 );
		ROM_LOAD( "eb02-2.rom",   0x10000, 0x10000, 0xf30d965d );
	
		ROM_REGION( 0x10000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "eb00.rom",	  0x00000, 0x10000, 0x6c1a14a8 );
	
		ROM_REGION( 0x0400, REGION_PROMS, 0 );
		ROM_LOAD( "eb05.rom",     0x0000, 0x0200, 0x3b6198cb );/* 82s147.084 */
		ROM_LOAD( "eb06.rom",     0x0200, 0x0200, 0x1fbd4b59 );/* 82s131.101 */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_spool3 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x14000, REGION_CPU1, 0 ); /* 64k for code + 16k for banks */
		ROM_LOAD( "eb04-2.rom",   0x10000, 0x4000, 0x0c7f2905 );
		ROM_CONTINUE(			  0x04000, 0xc000);
		/* 4000-7fff is banked but code falls through from 7fff to 8000, so */
		/* I have to load the bank directly at 4000. */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 ); /* audio cpu */
		ROM_LOAD( "eb03-2.rom",   0x10000, 0x8000, 0x9408ffb4 );
		ROM_CONTINUE(			  0x08000, 0x8000);
	
		ROM_REGION( 0x20000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "deco2.bin",	  0x00000, 0x10000, 0x0a23f0cf );
		ROM_LOAD( "deco3.bin",	  0x10000, 0x10000, 0x55ea7c45 );
	
		ROM_REGION( 0x10000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "eb00.rom",	  0x00000, 0x10000, 0x6c1a14a8 );
	
		ROM_REGION( 0x0400, REGION_PROMS, 0 );
		ROM_LOAD( "eb05.rom",     0x0000, 0x0200, 0x3b6198cb );/* 82s147.084 */
		ROM_LOAD( "eb06.rom",     0x0200, 0x0200, 0x1fbd4b59 );/* 82s131.101 */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_spool3i = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x14000, REGION_CPU1, 0 ); /* 64k for code + 16k for banks */
		ROM_LOAD( "de1.bin",	  0x10000, 0x4000, 0xa59980fe );
		ROM_CONTINUE(			  0x04000, 0xc000);
		/* 4000-7fff is banked but code falls through from 7fff to 8000, so */
		/* I have to load the bank directly at 4000. */
	
		ROM_REGION( 0x18000, REGION_CPU2, 0 ); /* audio cpu */
		ROM_LOAD( "eb03-2.rom",   0x10000, 0x8000, 0x9408ffb4 );
		ROM_CONTINUE(			  0x08000, 0x8000);
	
		ROM_REGION( 0x20000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "deco2.bin",	  0x00000, 0x10000, 0x0a23f0cf );
		ROM_LOAD( "deco3.bin",	  0x10000, 0x10000, 0x55ea7c45 );
	
		ROM_REGION( 0x10000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "eb00.rom",	  0x00000, 0x10000, 0x6c1a14a8 );
	
		ROM_REGION( 0x0400, REGION_PROMS, 0 );
		ROM_LOAD( "eb05.rom",     0x0000, 0x0200, 0x3b6198cb );/* 82s147.084 */
		ROM_LOAD( "eb06.rom",     0x0200, 0x0200, 0x1fbd4b59 );/* 82s131.101 */
	ROM_END(); }}; 
	
	/***************************************************************************/
	
	public static InitDriverPtr init_deco222 = new InitDriverPtr() { public void handler()
	{
		int A;
		UBytePtr rom = new UBytePtr(memory_region(REGION_CPU2));
		int diff = memory_region_length(REGION_CPU2) / 2;
	
	
		memory_set_opcode_base(1, new UBytePtr(rom, diff));
	
		/* bits 5 and 6 of the opcodes are swapped */
		for (A = 0;A < diff;A++)
			rom.write(A + diff, (rom.read(A) & 0x9f) | ((rom.read(A) & 0x20) << 1) | ((rom.read(A) & 0x40) >> 1));
	} };
	
	public static InitDriverPtr init_graphics = new InitDriverPtr() { public void handler()
	{
		UBytePtr rom = new UBytePtr(memory_region(REGION_GFX1));
		int len = memory_region_length(REGION_GFX1);
		int i,j;
                int[] temp=new int[16];
	
		/* Tile graphics roms have some swapped lines, original version only */
		for (i = 0x00000;i < len;i += 32)
		{
			for (j=0; j<16; j++)
			{
				temp[j] = rom.read(i+j+16);
				rom.write(i+j+16, rom.read(i+j));
				rom.write(i+j, temp[j]);
			}
		}
	} };
	
	public static InitDriverPtr init_pcktgal = new InitDriverPtr() { public void handler()
	{
		init_deco222.handler();
		init_graphics.handler();
	} };
	
	/***************************************************************************/
	
	public static GameDriver driver_pcktgal	   = new GameDriver("1987"	,"pcktgal"	,"pcktgal.java"	,rom_pcktgal,null	,machine_driver_pcktgal	,input_ports_pcktgal	,init_pcktgal	,ROT0	,	"Data East Corporation", "Pocket Gal (Japan)" );
	public static GameDriver driver_pcktgalb	   = new GameDriver("1989"	,"pcktgalb"	,"pcktgal.java"	,rom_pcktgalb,driver_pcktgal	,machine_driver_bootleg	,input_ports_pcktgal	,init_deco222	,ROT0	,	"bootleg", "Pocket Gal (bootleg)" );
	public static GameDriver driver_pcktgal2	   = new GameDriver("1989"	,"pcktgal2"	,"pcktgal.java"	,rom_pcktgal2,driver_pcktgal	,machine_driver_pcktgal	,input_ports_pcktgal	,init_graphics	,ROT0	,	"Data East Corporation", "Pocket Gal 2 (World?)" );
	public static GameDriver driver_spool3	   = new GameDriver("1989"	,"spool3"	,"pcktgal.java"	,rom_spool3,driver_pcktgal	,machine_driver_pcktgal	,input_ports_pcktgal	,init_graphics	,ROT0	,	"Data East Corporation", "Super Pool III (World?)" );
	public static GameDriver driver_spool3i	   = new GameDriver("1990"	,"spool3i"	,"pcktgal.java"	,rom_spool3i,driver_pcktgal	,machine_driver_pcktgal	,input_ports_pcktgal	,init_graphics	,ROT0	,	"Data East Corporation (I-Vics license)", "Super Pool III (I-Vics)" );
}
