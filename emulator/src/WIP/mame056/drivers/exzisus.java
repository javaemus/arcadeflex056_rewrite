/***************************************************************************

Exzisus
-------------------------------------
driver by Yochizo

This driver is heavily dependent on the Raine source.
Very thanks to Richard Bush and the Raine team.


Supported games :
==================
 Exzisus        (C) 1987 Taito


System specs :
===============
   CPU       : Z80(4 MHz) x 4
   Sound     : YM2151 x 1
   Chips     : TC0010VCU + TC0140SYT


Known issues :
===============
 - Dip switches are not known very much.
 - Very slow due to four Z80s.
 - Cocktail mode is not supported.

****************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP.mame056.sndhrdw.taitosnd.*;
import static WIP.mame056.vidhrdw.exzisus.*;

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
import static WIP2.mame056.sound.mixer.*;
import static WIP2.mame056.sound.mixerH.*;
import static WIP2.mame056.sound.namco.*;
import static WIP2.mame056.sound.namcoH.*;
import static WIP2.mame056.sound.ay8910.*;
import static WIP2.mame056.sound.ay8910H.*;
import static WIP2.mame056.sound._2151intf.*;
import static WIP2.mame056.sound._2151intfH.*;
import static WIP2.mame056.vidhrdw.generic.*;

import static WIP2.arcadeflex056.fileio.*;
import static WIP2.mame056.palette.*;

public class exzisus
{
	
	
	/***************************************************************************
	
	  Variables
	
	***************************************************************************/
	
	static UBytePtr exzisus_sharedram_ac = new UBytePtr();
	static UBytePtr exzisus_sharedram_bc = new UBytePtr();
	static int exzisus_cpua_bank = 0;
	static int exzisus_cpub_bank = 0;
	
	/***************************************************************************
	
	  Memory Handler(s)
	
	***************************************************************************/
	
	public static WriteHandlerPtr exzisus_cpua_bankswitch_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		UBytePtr RAM = new UBytePtr(memory_region(REGION_CPU1));
	
		if ( (data & 0x0f) != exzisus_cpua_bank )
		{
			exzisus_cpua_bank = data & 0x0f;
			if (exzisus_cpua_bank >= 2)
			{
				cpu_setbank( 1, new UBytePtr(RAM, 0x10000 + ( (exzisus_cpua_bank - 2) * 0x4000 ) ) );
			}
		}
	} };
	
	public static WriteHandlerPtr exzisus_cpub_bankswitch_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		UBytePtr RAM = new UBytePtr(memory_region(REGION_CPU4));
	
		if ( (data & 0x0f) != exzisus_cpub_bank )
		{
			exzisus_cpub_bank = data & 0x0f;
			if (exzisus_cpub_bank >= 2)
			{
				cpu_setbank( 2, new UBytePtr(RAM, 0x10000 + ( (exzisus_cpub_bank - 2) * 0x4000 ) ) );
			}
		}
	} };
	
	public static WriteHandlerPtr exzisus_coincounter_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		coin_lockout_w(0,~data & 0x01);
		coin_lockout_w(1,~data & 0x02);
		coin_counter_w.handler(0,data & 0x04);
		coin_counter_w.handler(1,data & 0x08);
	} };
	
	public static ReadHandlerPtr exzisus_sharedram_ac_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return exzisus_sharedram_ac.read(offset);
	} };
	
	public static ReadHandlerPtr exzisus_sharedram_bc_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return exzisus_sharedram_bc.read(offset);
	} };
	
	public static WriteHandlerPtr exzisus_sharedram_ac_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		exzisus_sharedram_ac.write(offset, data);
	} };
	
	public static WriteHandlerPtr exzisus_sharedram_bc_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		exzisus_sharedram_bc.write(offset, data);
	} };
	
	
	/**************************************************************************
	
	  Memory Map(s)
	
	**************************************************************************/
	
	public static InitDriverPtr init_exzisus = new InitDriverPtr() { public void handler()
	{
		UBytePtr RAM = new UBytePtr(memory_region(REGION_CPU4));
	
		/* Fix ROM 1 error */
		RAM.write(0x6829, 0x18);
	
		/* Fix WORK RAM error */
		RAM.write(0x67fd, 0x18);
	} };
	
	static WriteYmHandlerPtr irqhandler = new WriteYmHandlerPtr() {
            public void handler(int irq) {
                cpu_set_irq_line(1, 0, irq!=0 ? ASSERT_LINE : CLEAR_LINE);
            }
        };
	
	static YM2151interface ym2151_interface = new YM2151interface
	(
		1,			/* 1 chip */
		4000000,	/* 4 MHz ? */
		new int[]{ YM3012_VOL(50,MIXER_PAN_CENTER,50,MIXER_PAN_CENTER) },
		new WriteYmHandlerPtr[]{ irqhandler }
	);
	
	public static Memory_ReadAddress cpua_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_BANK1 ),
		new Memory_ReadAddress( 0xc000, 0xc5ff, exzisus_objectram_0_r ),
		new Memory_ReadAddress( 0xc600, 0xdfff, exzisus_videoram_0_r ),
		new Memory_ReadAddress( 0xe000, 0xefff, MRA_RAM ),
		new Memory_ReadAddress( 0xf000, 0xf000, MRA_NOP ),
		new Memory_ReadAddress( 0xf001, 0xf001, taitosound_comm_r ),
		new Memory_ReadAddress( 0xf400, 0xf400, input_port_0_r ),
		new Memory_ReadAddress( 0xf401, 0xf401, input_port_1_r ),
		new Memory_ReadAddress( 0xf402, 0xf402, input_port_2_r ),
		new Memory_ReadAddress( 0xf404, 0xf404, input_port_3_r ),
		new Memory_ReadAddress( 0xf405, 0xf405, input_port_4_r ),
		new Memory_ReadAddress( 0xf800, 0xffff, exzisus_sharedram_ac_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress cpua_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0xbfff, MWA_ROM ),
		new Memory_WriteAddress( 0xc000, 0xc5ff, exzisus_objectram_0_w, exzisus_objectram0, exzisus_objectram_size0 ),
		new Memory_WriteAddress( 0xc600, 0xdfff, exzisus_videoram_0_w, exzisus_videoram0 ),
		new Memory_WriteAddress( 0xe000, 0xefff, MWA_RAM ),
		new Memory_WriteAddress( 0xf000, 0xf000, taitosound_port_w ),
		new Memory_WriteAddress( 0xf001, 0xf001, taitosound_comm_w ),
		new Memory_WriteAddress( 0xf400, 0xf400, exzisus_cpua_bankswitch_w ),
		new Memory_WriteAddress( 0xf402, 0xf402, exzisus_coincounter_w ),
		new Memory_WriteAddress( 0xf800, 0xffff, exzisus_sharedram_ac_w ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress cpub_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0x85ff, exzisus_objectram_1_r ),
		new Memory_ReadAddress( 0x8600, 0x9fff, exzisus_videoram_1_r ),
		new Memory_ReadAddress( 0xa000, 0xafff, exzisus_sharedram_bc_r ),
		new Memory_ReadAddress( 0xb000, 0xbfff, MRA_RAM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress cpub_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),
		new Memory_WriteAddress( 0x8000, 0x85ff, exzisus_objectram_1_w ),
		new Memory_WriteAddress( 0x8600, 0x9fff, exzisus_videoram_1_w ),
		new Memory_WriteAddress( 0xa000, 0xafff, exzisus_sharedram_bc_w ),
		new Memory_WriteAddress( 0xb000, 0xbfff, MWA_RAM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress cpuc_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_BANK2 ),
		new Memory_ReadAddress( 0xc000, 0xc5ff, exzisus_objectram_1_r ),
		new Memory_ReadAddress( 0xc600, 0xdfff, exzisus_videoram_1_r ),
		new Memory_ReadAddress( 0xe000, 0xefff, exzisus_sharedram_bc_r ),
		new Memory_ReadAddress( 0xf800, 0xffff, exzisus_sharedram_ac_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress cpuc_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0xbfff, MWA_ROM ),
		new Memory_WriteAddress( 0xc000, 0xc5ff, exzisus_objectram_1_w, exzisus_objectram1, exzisus_objectram_size1 ),
		new Memory_WriteAddress( 0xc600, 0xdfff, exzisus_videoram_1_w, exzisus_videoram1 ),
		new Memory_WriteAddress( 0xe000, 0xefff, exzisus_sharedram_bc_w, exzisus_sharedram_bc ),
		new Memory_WriteAddress( 0xf400, 0xf400, exzisus_cpub_bankswitch_w ),
		new Memory_WriteAddress( 0xf800, 0xffff, exzisus_sharedram_ac_w, exzisus_sharedram_ac ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress sound_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),
		new Memory_ReadAddress( 0x8000, 0x8fff, MRA_RAM ),
		new Memory_ReadAddress( 0x9000, 0x9000, MRA_NOP ),
		new Memory_ReadAddress( 0x9001, 0x9001, YM2151_status_port_0_r ),
		new Memory_ReadAddress( 0xa000, 0xa000, MRA_NOP ),
		new Memory_ReadAddress( 0xa001, 0xa001, taitosound_slave_comm_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress sound_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),
		new Memory_WriteAddress( 0x8000, 0x8fff, MWA_RAM ),
		new Memory_WriteAddress( 0x9000, 0x9000, YM2151_register_port_0_w ),
		new Memory_WriteAddress( 0x9001, 0x9001, YM2151_data_port_0_w ),
		new Memory_WriteAddress( 0xa000, 0xa000, taitosound_slave_port_w ),
		new Memory_WriteAddress( 0xa001, 0xa001, taitosound_slave_comm_w ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	/***************************************************************************
	
	  Input Port(s)
	
	***************************************************************************/
	
	public static void EXZISUS_PLAYERS_INPUT( int player ){
		PORT_START();  
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_UP    | IPF_8WAY | player );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY | player );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | player );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY | player );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON1 | player );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON2 | player );
        }
	public static void TAITO_COINAGE_JAPAN_8(){
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Coin_A") ); 
		PORT_DIPSETTING(    0x10, DEF_STR( "2C_1C") ); 
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_1C") ); 
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_3C") ); 
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_2C") ); 
		PORT_DIPNAME( 0xc0, 0xc0, DEF_STR( "Coin_B") ); 
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") ); 
		PORT_DIPSETTING(    0xc0, DEF_STR( "1C_1C") ); 
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_3C") ); 
		PORT_DIPSETTING(    0x80, DEF_STR( "1C_2C") );
        }
	
	public static void TAITO_DIFFICULTY_8(){
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Difficulty") ); 
		PORT_DIPSETTING(    0x02, "Easy" );
		PORT_DIPSETTING(    0x03, "Medium" );
		PORT_DIPSETTING(    0x01, "Hard" );
		PORT_DIPSETTING(    0x00, "Hardest" );
        }
        
	static InputPortPtr input_ports_exzisus = new InputPortPtr(){ public void handler() { 
	/* IN0 */
		EXZISUS_PLAYERS_INPUT( IPF_PLAYER1 );
		PORT_BIT( 0xc0, IP_ACTIVE_LOW, IPT_UNKNOWN );
		/* IN1 */
		EXZISUS_PLAYERS_INPUT( IPF_PLAYER2 );
		PORT_BIT( 0xc0, IP_ACTIVE_LOW, IPT_UNKNOWN );
		/* IN2 */
		PORT_START();       /* System control (2) */
		PORT_BIT( 0x01, IP_ACTIVE_LOW,  IPT_SERVICE1 );	PORT_BIT( 0x02, IP_ACTIVE_LOW,  IPT_TILT );	PORT_BIT( 0x04, IP_ACTIVE_LOW,  IPT_START1 );	PORT_BIT( 0x08, IP_ACTIVE_LOW,  IPT_START2 );	PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_COIN1 );	PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_COIN2 );	PORT_BIT( 0xc0, IP_ACTIVE_LOW,  IPT_UNKNOWN );
		PORT_START();   /* DSW 1 (3) */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Flip_Screen") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_SERVICE( 0x04,	IP_ACTIVE_LOW );	/* Service Mode */
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		TAITO_COINAGE_JAPAN_8();
	
		PORT_START();   /* DSW 2 (4) */
		TAITO_DIFFICULTY_8();
		PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x08, "100k and every 150k" );	PORT_DIPSETTING(    0x04, "150k" );	PORT_DIPSETTING(    0x0c, "150k and every 200k" );	PORT_DIPSETTING(    0x00, "200k" );	PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "2" );	PORT_DIPSETTING(    0x30, "3" );	PORT_DIPSETTING(    0x20, "4" );	PORT_DIPSETTING(    0x10, "5" );	PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	INPUT_PORTS_END(); }}; 
	
	
	/***************************************************************************
	
	  Machine Driver(s)
	
	***************************************************************************/
	
	static GfxLayout charlayout = new GfxLayout
	(
		8, 8,
		8*2048,
		4,
		new int[] { 0x40000*8, 0x40000*8+4, 0, 4 },
		new int[] { 3, 2, 1, 0, 8+3, 8+2, 8+1, 8+0 },
		new int[] { 0*16, 1*16, 2*16, 3*16, 4*16, 5*16, 6*16, 7*16 },
		16*8
	);
	
	static GfxDecodeInfo exzisus_gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, charlayout,   0, 256 ),
		new GfxDecodeInfo( REGION_GFX2, 0, charlayout, 256, 256 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	static MachineDriver machine_driver_exzisus = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				6000000,			/* 6 MHz ??? */
				cpua_readmem, cpua_writemem, null, null,
				interrupt, 1
			),
			new MachineCPU(
				CPU_Z80,
				4000000,			/* 4 MHz ??? */
				sound_readmem, sound_writemem, null, null,
				ignore_interrupt, 1
			),
			new MachineCPU(
				CPU_Z80,
				6000000,			/* 6 MHz ??? */
				cpub_readmem, cpub_writemem, null, null,
				interrupt, 1
			),
			new MachineCPU(
				CPU_Z80,
				6000000,			/* 6 MHz ??? */
				cpuc_readmem, cpuc_writemem, null, null,
				interrupt, 1
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		10,	/* 10 CPU slices per frame - enough for the sound CPU to read all commands */
		null,
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 30*8-1 ),
		exzisus_gfxdecodeinfo,
		1024, 0,
		palette_RRRR_GGGG_BBBB_convert_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		null,
		null,
		exzisus_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM2151,
				ym2151_interface
			)
		}
	);
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	static RomLoadPtr rom_exzisus = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x48000, REGION_CPU1, 0 );    				/* Z80 CPU A */
		ROM_LOAD( "b23-11.bin", 0x00000, 0x08000, 0xd6a79cef );	ROM_CONTINUE(           0x10000, 0x08000 );	ROM_LOAD( "b12-12.bin", 0x18000, 0x10000, 0xa662be67 );	ROM_LOAD( "b12-13.bin", 0x28000, 0x10000, 0x04a29633 );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );    				/* Z80 for Sound */
		ROM_LOAD( "b23-14.bin",  0x00000, 0x08000, 0xf7ca7df2 );
		ROM_REGION( 0x10000, REGION_CPU3, 0 );    				/* Z80 CPU B */
		ROM_LOAD( "b23-13.bin",  0x00000, 0x08000, 0x51110aa1 );
		ROM_REGION( 0x48000, REGION_CPU4, 0 );					/* Z80 CPU C */
		ROM_LOAD( "b23-10.bin", 0x00000, 0x08000, 0xc80216fc );	ROM_CONTINUE(           0x10000, 0x08000 );	ROM_LOAD( "b23-12.bin", 0x18000, 0x10000, 0x13637f54 );
		ROM_REGION( 0x80000, REGION_GFX1, ROMREGION_DISPOSE | ROMREGION_INVERT );/* BG 0 */
		ROM_LOAD( "b12-16.bin",  0x00000, 0x10000, 0x6fec6acb );	ROM_LOAD( "b12-18.bin",  0x10000, 0x10000, 0x64e358aa );	ROM_LOAD( "b12-20.bin",  0x20000, 0x10000, 0x87f52e89 );	ROM_LOAD( "b12-15.bin",  0x40000, 0x10000, 0xd81107c8 );	ROM_LOAD( "b12-17.bin",  0x50000, 0x10000, 0xdb1d5a6c );	ROM_LOAD( "b12-19.bin",  0x60000, 0x10000, 0x772b2641 );
		ROM_REGION( 0x80000, REGION_GFX2, ROMREGION_DISPOSE | ROMREGION_INVERT );/* BG 1 */
		ROM_LOAD( "b23-06.bin",  0x00000, 0x10000, 0x44f8f661 );	ROM_LOAD( "b23-08.bin",  0x10000, 0x10000, 0x1ce498c1 );	ROM_LOAD( "b23-07.bin",  0x40000, 0x10000, 0xd7f6ec89 );	ROM_LOAD( "b23-09.bin",  0x50000, 0x10000, 0x6651617f );
		ROM_REGION( 0x00c00, REGION_PROMS, 0 );				/* PROMS */
		ROM_LOAD( "b23-04.bin",  0x00000, 0x00400, 0x5042cffa );	ROM_LOAD( "b23-03.bin",  0x00400, 0x00400, 0x9458fd45 );	ROM_LOAD( "b23-05.bin",  0x00800, 0x00400, 0x87f0f69a );ROM_END(); }}; 
	
	
	/*  ( YEAR      NAME  PARENT  MACHINE    INPUT     INIT  MONITOR  COMPANY              FULLNAME ) */
	public static GameDriver driver_exzisus	   = new GameDriver("1987"	,"exzisus"	,"exzisus.java"	,rom_exzisus,null	,machine_driver_exzisus	,input_ports_exzisus	,init_exzisus	,ROT0	,	"Taito Corporation", "Exzisus (Japan)", GAME_NO_COCKTAIL );
}
