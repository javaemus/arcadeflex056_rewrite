/*****************************************************************************

XX Mission (c) 1986 UPL

	Driver by Uki

	31/Mar/2001 -

*****************************************************************************/

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
import static WIP2.mame056.mame.*;
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
import static WIP2.mame056.sound.dac.*;
import static WIP2.mame056.sound.dacH.*;
import static WIP2.mame056.timer.*;
import static WIP2.mame056.timerH.*;

import static WIP2.mame056.vidhrdw.generic.*;

import static WIP2.arcadeflex056.fileio.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.inptport.*;

import static WIP.mame056.vidhrdw.xxmissio.*;
import static WIP2.mame056.palette.paletteram_r;
import static WIP2.mame056.sound._2203intf.*;
import static WIP2.mame056.sound._2203intfH.*;

public class xxmissio
{
	static UBytePtr shared_workram = new UBytePtr();
	
	static int xxmissio_status;
	
	
	
	
	
	public static WriteHandlerPtr shared_workram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		shared_workram.write(offset ^ 0x1000, data);
	} };
	
	public static ReadHandlerPtr shared_workram_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return shared_workram.read(offset ^ 0x1000);
	} };
	
	public static WriteHandlerPtr xxmissio_bank_sel_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		UBytePtr BANK = new UBytePtr(memory_region(REGION_USER1));
		int bank_address = (data & 0x07) * 0x4000;
		cpu_setbank(1, new UBytePtr(BANK, bank_address));
	} };
	
	public static ReadHandlerPtr xxmissio_status_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		xxmissio_status = (xxmissio_status | 2) & ( readinputport(4) | 0xfd );
		return xxmissio_status;
	} };
	
	public static WriteHandlerPtr xxmissio_status_m_w = new WriteHandlerPtr() {
            public void handler(int offset, int data) {
                switch (data)
		{
			case 0x00:
				xxmissio_status |= 0x20;
				break;
	
			case 0x40:
				xxmissio_status &= ~0x08;
				cpu_cause_interrupt(1,0x10);
				break;
	
			case 0x80:
				xxmissio_status |= 0x04;
				break;
		}
            }
        };
	
	public static WriteHandlerPtr xxmissio_status_s_w = new WriteHandlerPtr() {
            public void handler(int offset, int data) {
		switch (data)
		{
			case 0x00:
				xxmissio_status |= 0x10;
				break;
	
			case 0x40:
				xxmissio_status |= 0x08;
				break;
	
			case 0x80:
				xxmissio_status &= ~0x04;
				cpu_cause_interrupt(0,0x10);
				break;
		}
	}};
	
	public static InterruptPtr xxmissio_interrupt_m = new InterruptPtr() { public int handler() 
	{
		xxmissio_status &= ~0x20;
		return interrupt.handler();
	} };
	
	public static InterruptPtr xxmissio_interrupt_s = new InterruptPtr() { public int handler() 
	{
		xxmissio_status &= ~0x10;
		return interrupt.handler();
	} };
	
	/****************************************************************************/
	
	public static Memory_ReadAddress readmem1[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
	
		new Memory_ReadAddress( 0x8000, 0x8000, YM2203_status_port_0_r ),
		new Memory_ReadAddress( 0x8001, 0x8001, YM2203_read_port_0_r ),
		new Memory_ReadAddress( 0x8002, 0x8002, YM2203_status_port_1_r ),
		new Memory_ReadAddress( 0x8003, 0x8003, YM2203_read_port_1_r ),
	
		new Memory_ReadAddress( 0xa000, 0xa000, input_port_0_r ),
		new Memory_ReadAddress( 0xa001, 0xa001, input_port_1_r ),
	
		new Memory_ReadAddress( 0xa002, 0xa002, xxmissio_status_r ),
	
		new Memory_ReadAddress( 0xc000, 0xc7ff, xxmissio_fgram_r ),
		new Memory_ReadAddress( 0xc800, 0xcfff, xxmissio_videoram_r ),
		new Memory_ReadAddress( 0xd000, 0xd7ff, spriteram_r ),
	
		new Memory_ReadAddress( 0xd800, 0xdaff, paletteram_r ),
	
		new Memory_ReadAddress( 0xe000, 0xffff, MRA_RAM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress writemem1[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),
	
		new Memory_WriteAddress( 0x8000, 0x8000, YM2203_control_port_0_w ),
		new Memory_WriteAddress( 0x8001, 0x8001, YM2203_write_port_0_w ),
		new Memory_WriteAddress( 0x8002, 0x8002, YM2203_control_port_1_w ),
		new Memory_WriteAddress( 0x8003, 0x8003, YM2203_write_port_1_w ),
	
		new Memory_WriteAddress( 0xa002, 0xa002, xxmissio_status_m_w ),
	
		new Memory_WriteAddress( 0xa003, 0xa003, xxmissio_flipscreen_w ),
	
		new Memory_WriteAddress( 0xc000, 0xc7ff, xxmissio_fgram_w, xxmissio_fgram, xxmissio_fgram_size ),
		new Memory_WriteAddress( 0xc800, 0xcfff, xxmissio_videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0xd000, 0xd7ff, MWA_RAM, spriteram, spriteram_size ),
	
		new Memory_WriteAddress( 0xd800, 0xdaff, xxmissio_paletteram_w, paletteram ),
	
		new Memory_WriteAddress( 0xe000, 0xffff, MWA_RAM, shared_workram ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress readmem2[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x3fff, MRA_ROM ),
		new Memory_ReadAddress( 0x4000, 0x7fff, MRA_BANK1 ),
	
		new Memory_ReadAddress( 0x8000, 0x8000, YM2203_status_port_0_r ),
		new Memory_ReadAddress( 0x8001, 0x8001, YM2203_read_port_0_r ),
		new Memory_ReadAddress( 0x8002, 0x8002, YM2203_status_port_1_r ),
		new Memory_ReadAddress( 0x8003, 0x8003, YM2203_read_port_1_r ),
	
		new Memory_ReadAddress( 0xa000, 0xa000, input_port_0_r ),
		new Memory_ReadAddress( 0xa001, 0xa001, input_port_1_r ),
	
		new Memory_ReadAddress( 0xa002, 0xa002, xxmissio_status_r ),
	
		new Memory_ReadAddress( 0xc000, 0xc7ff, xxmissio_fgram_r ),
		new Memory_ReadAddress( 0xc800, 0xcfff, xxmissio_videoram_r ),
		new Memory_ReadAddress( 0xd000, 0xd7ff, spriteram_r ),
	
		new Memory_ReadAddress( 0xd800, 0xdaff, paletteram_r ),
	
		new Memory_ReadAddress( 0xe000, 0xffff, shared_workram_r ),
	
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress writemem2[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x3fff, MWA_ROM ),
		new Memory_WriteAddress( 0x4000, 0x7fff, MWA_BANK1 ),
	
		new Memory_WriteAddress( 0x8000, 0x8000, YM2203_control_port_0_w ),
		new Memory_WriteAddress( 0x8001, 0x8001, YM2203_write_port_0_w ),
		new Memory_WriteAddress( 0x8002, 0x8002, YM2203_control_port_1_w ),
		new Memory_WriteAddress( 0x8003, 0x8003, YM2203_write_port_1_w ),
	
		new Memory_WriteAddress( 0x8006, 0x8006, xxmissio_bank_sel_w ),
	
		new Memory_WriteAddress( 0xa002, 0xa002, xxmissio_status_s_w ),
	
		new Memory_WriteAddress( 0xa003, 0xa003, xxmissio_flipscreen_w ),
	
		new Memory_WriteAddress( 0xc000, 0xc7ff, xxmissio_fgram_w ),
		new Memory_WriteAddress( 0xc800, 0xcfff, xxmissio_videoram_w ),
		new Memory_WriteAddress( 0xd000, 0xd7ff, spriteram_w ),
	
		new Memory_WriteAddress( 0xd800, 0xdaff, xxmissio_paletteram_w ),
	
		new Memory_WriteAddress( 0xe000, 0xffff, shared_workram_w ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	/****************************************************************************/
	
	static InputPortPtr input_ports_xxmissio = new InputPortPtr(){ public void handler() { 
	PORT_START(); 
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY );	PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY );
		PORT_START(); 
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_COIN1 );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_COCKTAIL );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_COCKTAIL );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY | IPF_COCKTAIL );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY | IPF_COCKTAIL );	PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY | IPF_COCKTAIL );
		PORT_START(); 	/* DSW1 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0x00, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x04, 0x04, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x04, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x10, "Unknown 1-5" );/* difficulty?? */
		PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x20, DEF_STR( "Cocktail") );
		PORT_BITX(    0x40, 0x40, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Endless Game", IP_KEY_NONE, IP_JOY_NONE );	PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x80, IP_ACTIVE_LOW );
		PORT_START(); 	/* DSW2 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x01, "2" );	PORT_DIPSETTING(    0x03, "3" );	PORT_DIPSETTING(    0x02, "4" );	PORT_DIPSETTING(    0x00, "5" );	PORT_DIPNAME( 0x04, 0x04, "First Bonus" );	PORT_DIPSETTING(    0x04, "30000" );	PORT_DIPSETTING(    0x00, "40000" );	PORT_DIPNAME( 0x18, 0x08, "Bonus Every" );	PORT_DIPSETTING(    0x18, "50000" );	PORT_DIPSETTING(    0x08, "70000" );	PORT_DIPSETTING(    0x10, "90000" );	PORT_DIPSETTING(    0x00, "None" );	PORT_DIPNAME( 0x20, 0x20, "Unknown 2-6" );	PORT_DIPSETTING(    0x20, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x40, "Unknown 2-7" );	PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, "Unknown 2-8" );	PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START(); 
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_VBLANK );INPUT_PORTS_END(); }}; 
	
	/****************************************************************************/
	
	static GfxLayout charlayout = new GfxLayout
	(
		16,8,   /* 16*8 characters */
		2048,   /* 2048 characters */
		4,      /* 4 bits per pixel */
		new int[] {0,1,2,3},
		new int[] {0,4,8,12,16,20,24,28,32,36,40,44,48,52,56,60},
		new int[] {64*0, 64*1, 64*2, 64*3, 64*4, 64*5, 64*6, 64*7},
		64*8
	);
	
	static GfxLayout spritelayout = new GfxLayout
	(
		32,16,    /* 32*16 characters */
		512,	  /* 512 sprites */
		4,        /* 4 bits per pixel */
		new int[] {0,1,2,3},
		new int[] {0,4,8,12,16,20,24,28,
		 32,36,40,44,48,52,56,60,
		 8*64+0,8*64+4,8*64+8,8*64+12,8*64+16,8*64+20,8*64+24,8*64+28,
		 8*64+32,8*64+36,8*64+40,8*64+44,8*64+48,8*64+52,8*64+56,8*64+60},
		new int[] {64*0, 64*1, 64*2, 64*3, 64*4, 64*5, 64*6, 64*7,
		 64*16, 64*17, 64*18, 64*19, 64*20, 64*21, 64*22, 64*23},
		64*8*4
	);
	
	static GfxLayout bglayout = new GfxLayout
	(
		16,8,   /* 16*8 characters */
		1024,   /* 1024 characters */
		4,      /* 4 bits per pixel */
		new int[] {0,1,2,3},
		new int[] {0,4,8,12,16,20,24,28,32,36,40,44,48,52,56,60},
		new int[] {64*0, 64*1, 64*2, 64*3, 64*4, 64*5, 64*6, 64*7},
		64*8
	);
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0x0000, charlayout,   256,  8 ), /* FG */
		new GfxDecodeInfo( REGION_GFX1, 0x0000, spritelayout,   0,  8 ), /* sprite */
		new GfxDecodeInfo( REGION_GFX2, 0x0000, bglayout,     512, 16 ), /* BG */
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	/****************************************************************************/
	
	static YM2203interface ym2203_interface = new YM2203interface
	(
		2,          /* 2 chips */
		12000000/8,    /* 1.5 MHz */
		new int[]{ YM2203_VOL(40,15), YM2203_VOL(40,15) },
		new ReadHandlerPtr[]{ input_port_2_r, null },
		new ReadHandlerPtr[]{ input_port_3_r, null },
		new WriteHandlerPtr[]{ null, xxmissio_scroll_x_w },
		new WriteHandlerPtr[]{ null, xxmissio_scroll_y_w }
	);
	
	static MachineDriver machine_driver_xxmissio = new MachineDriver
	(
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				12000000/4,	/* 3.0MHz */
				readmem1,writemem1,null,null,
				xxmissio_interrupt_m,1
			),
			new MachineCPU(
				CPU_Z80,
				12000000/4,	/* 3.0MHz */
				readmem2,writemem2,null,null,
				xxmissio_interrupt_s,2
			)
		},
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,
		100,
		null,
	
		64*8, 32*8, new rectangle( 0*8, 64*8-1, 4*8, 28*8-1 ),
	
		gfxdecodeinfo,
		768, 0,
		null,
	
		VIDEO_TYPE_RASTER | VIDEO_PIXEL_ASPECT_RATIO_1_2,
		null,
		generic_vh_start,
		generic_vh_stop,
		xxmissio_vh_screenrefresh,
	
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2203,
				ym2203_interface
			)
		}
	);
	
	/****************************************************************************/
	
	static RomLoadPtr rom_xxmissio = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* CPU1 */
		ROM_LOAD( "xx1.4l", 0x0000,  0x8000, 0x86e07709 );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* CPU2 */
		ROM_LOAD( "xx2.4b", 0x0000,  0x4000, 0x13fa7049 );
		ROM_REGION( 0x18000, REGION_USER1, 0 );/* BANK */
		ROM_LOAD( "xx3.6a", 0x00000,  0x8000, 0x16fdacab );	ROM_LOAD( "xx4.6b", 0x08000,  0x8000, 0x274bd4d2);	ROM_LOAD( "xx5.6d", 0x10000,  0x8000, 0xc5f35535 );
		ROM_REGION( 0x20000, REGION_GFX1, ROMREGION_DISPOSE );/* FG/sprites */
		ROM_LOAD16_BYTE( "xx6.8j", 0x00001, 0x8000, 0xdc954d01 );	ROM_LOAD16_BYTE( "xx8.8f", 0x00000, 0x8000, 0xa9587cc6 );	ROM_LOAD16_BYTE( "xx7.8h", 0x10001, 0x8000, 0xabe9cd68 );	ROM_LOAD16_BYTE( "xx9.8e", 0x10000, 0x8000, 0x854e0e5f );
		ROM_REGION( 0x10000, REGION_GFX2, ROMREGION_DISPOSE );/* BG */
		ROM_LOAD16_BYTE( "xx10.4c", 0x0000,  0x8000, 0xd27d7834 );	ROM_LOAD16_BYTE( "xx11.4b", 0x0001,  0x8000, 0xd9dd827c );ROM_END(); }}; 
	
	public static GameDriver driver_xxmissio	   = new GameDriver("1986"	,"xxmissio"	,"xxmissio.java"	,rom_xxmissio,null	,machine_driver_xxmissio	,input_ports_xxmissio	,null	,ROT90	,	"UPL", "XX Mission" );
}
