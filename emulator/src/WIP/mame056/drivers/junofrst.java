/***************************************************************************

Juno First :  memory map same as tutankham with some address changes
Chris Hardy (chrish@kcbbs.gen.nz)

Thanks to Rob Jarret for the original Tutankham memory map on which both the
Juno First emu and the mame driver is based on.

		Juno First memory map by Chris Hardy

Read/Write memory

$0000-$7FFF = Screen RAM (only written to)
$8000-$800f = Palette RAM. BBGGGRRR (D7->D0)
$8100-$8FFF = Work RAM

Write memory

$8030	- interrupt control register D0 = interupts on or off
$8031	- unknown
$8032	- unknown
$8033	- unknown
$8034	- flip screen x
$8035	- flip screen y

$8040	- Sound CPU req/ack data
$8050	- Sound CPU command data
$8060	- Banked memory page select.
$8070/1 - Blitter source data word
$8072/3 - Blitter destination word. Write to $8073 triggers a blit

Read memory

$8010	- Dipswitch 2
$801c	- Watchdog
$8020	- Start/Credit IO
				D2 = Credit 1
				D3 = Start 1
				D4 = Start 2
$8024	- P1 IO
				D0 = left
				D1 = right
				D2 = up
				D3 = down
				D4 = fire 2
				D5 = fire 1

$8028	- P2 IO - same as P1 IO
$802c	- Dipswitch 1



$9000->$9FFF Banked Memory - see below
$A000->$BFFF "juno\\JFA_B9.BIN",
$C000->$DFFF "juno\\JFB_B10.BIN",
$E000->$FFFF "juno\\JFC_A10.BIN",

Banked memory - Paged into $9000->$9FFF..

NOTE - In Tutankhm this only contains graphics, in Juno First it also contains code. (which
		generally sets up the blitter)

	"juno\\JFC1_A4.BIN",	$0000->$1FFF
	"juno\\JFC2_A5.BIN",	$2000->$3FFF
	"juno\\JFC3_A6.BIN",	$4000->$5FFF
	"juno\\JFC4_A7.BIN",	$6000->$7FFF
	"juno\\JFC5_A8.BIN",	$8000->$9FFF
	"juno\\JFC6_A9.BIN",	$A000->$bFFF

Blitter source graphics

	"juno\\JFS3_C7.BIN",	$C000->$DFFF
	"juno\\JFS4_D7.BIN",	$E000->$FFFF
	"juno\\JFS5_E7.BIN",	$10000->$11FFF


***************************************************************************/


/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;
import static WIP2.common.ptr.*;
import static WIP2.mame056.commonH.*;
import static WIP2.mame056.common.*;
import static WIP2.mame056.cpu.i8039.i8039.*;
import static WIP2.mame056.cpu.i8039.i8039H.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.mame056.memory.*;
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.vidhrdw.generic.*;
import static WIP2.mame056.vidhrdw.tutankhm.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sndintrfH.*;
import static WIP2.mame056.sound.ay8910.*;
import static WIP2.mame056.sound.ay8910H.*;
import static WIP2.mame056.sound.dac.*;
import static WIP2.mame056.sound.dacH.*;
import static WIP2.mame056.sound.streams.*;

import static WIP2.mame056.machine.konami.*;

public class junofrst
{
	public static WriteHandlerPtr junofrst_bankselect_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		int bankaddress;
		UBytePtr RAM = new UBytePtr(memory_region(REGION_CPU1));
	
		bankaddress = 0x10000 + (data & 0x0f) * 0x1000;
		cpu_setbank(1,new UBytePtr(RAM, bankaddress));
	} };
	
	
	static int i8039_irqenable;
	static int i8039_status;
	
	public static ReadHandlerPtr junofrst_portA_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int timer;
	
	
		/* main xtal 14.318MHz, divided by 8 to get the CPU clock, further */
		/* divided by 1024 to get this timer */
		/* (divide by (1024/2), and not 1024, because the CPU cycle counter is */
		/* incremented every other state change of the clock) */
		timer = (cpu_gettotalcycles() / (1024/2)) & 0x0f;
	
		/* low three bits come from the 8039 */
	
		return (timer << 4) | i8039_status;
	} };
	
	public static WriteHandlerPtr junofrst_portB_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		int i;
	
	
		for (i = 0;i < 3;i++)
		{
			int C;
	
	
			C = 0;
			if ((data & 1)!=0) C += 47000;	/* 47000pF = 0.047uF */
			if ((data & 2)!=0) C += 220000;	/* 220000pF = 0.22uF */
			data >>= 2;
			set_RC_filter(i,1000,2200,200,C);
		}
	} };
        
        static int last;
	
	public static WriteHandlerPtr junofrst_sh_irqtrigger_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (last == 0 && data == 1)
		{
			/* setting bit 0 low then high triggers IRQ on the sound CPU */
			cpu_cause_interrupt(1,0xff);
		}
	
		last = data;
	} };
	
	public static WriteHandlerPtr junofrst_i8039_irq_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if (i8039_irqenable != 0)
			cpu_cause_interrupt(2,I8039_EXT_INT);
	} };
	
	public static WriteHandlerPtr i8039_irqen_and_status_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		i8039_irqenable = data & 0x80;
		i8039_status = (data & 0x70) >> 4;
	} };
	
	public static WriteHandlerPtr flip_screen_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		flip_screen_set(data);
	} };
	
	public static WriteHandlerPtr junofrst_coin_counter_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		coin_counter_w.handler(offset,data);
	} };
	
	
	
	public static Memory_ReadAddress readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x7fff, MRA_RAM ),
		new Memory_ReadAddress( 0x8010, 0x8010, input_port_0_r ),	/* DSW2 (inverted bits) */
		new Memory_ReadAddress( 0x801c, 0x801c, watchdog_reset_r ),
		new Memory_ReadAddress( 0x8020, 0x8020, input_port_1_r ),	/* IN0 I/O: Coin slots, service, 1P/2P buttons */
		new Memory_ReadAddress( 0x8024, 0x8024, input_port_2_r ),	/* IN1: Player 1 I/O */
		new Memory_ReadAddress( 0x8028, 0x8028, input_port_3_r ),	/* IN2: Player 2 I/O */
		new Memory_ReadAddress( 0x802c, 0x802c, input_port_4_r ),	/* DSW1 (inverted bits) */
		new Memory_ReadAddress( 0x8100, 0x8fff, MRA_RAM ),
		new Memory_ReadAddress( 0x9000, 0x9fff, MRA_BANK1 ),
		new Memory_ReadAddress( 0xa000, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x7fff, tutankhm_videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0x8000, 0x800f, paletteram_BBGGGRRR_w, paletteram ),
		new Memory_WriteAddress( 0x8030, 0x8030, interrupt_enable_w ),
		new Memory_WriteAddress( 0x8031, 0x8032, junofrst_coin_counter_w ),
		new Memory_WriteAddress( 0x8033, 0x8033, MWA_RAM, tutankhm_scrollx ),              /* video x pan hardware reg - Not USED in Juno*/
		new Memory_WriteAddress( 0x8034, 0x8035, flip_screen_w ),
		new Memory_WriteAddress( 0x8040, 0x8040, junofrst_sh_irqtrigger_w ),
		new Memory_WriteAddress( 0x8050, 0x8050, soundlatch_w ),
		new Memory_WriteAddress( 0x8060, 0x8060, junofrst_bankselect_w ),
		new Memory_WriteAddress( 0x8070, 0x8073, junofrst_blitter_w ),
		new Memory_WriteAddress( 0x8100, 0x8fff, MWA_RAM ),
		new Memory_WriteAddress( 0x9000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	public static Memory_ReadAddress sound_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x0fff, MRA_ROM ),
		new Memory_ReadAddress( 0x2000, 0x23ff, MRA_RAM ),
		new Memory_ReadAddress( 0x3000, 0x3000, soundlatch_r ),
		new Memory_ReadAddress( 0x4001, 0x4001, AY8910_read_port_0_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress sound_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x0fff, MWA_ROM ),
		new Memory_WriteAddress( 0x2000, 0x23ff, MWA_RAM ),
		new Memory_WriteAddress( 0x4000, 0x4000, AY8910_control_port_0_w ),
		new Memory_WriteAddress( 0x4002, 0x4002, AY8910_write_port_0_w ),
		new Memory_WriteAddress( 0x5000, 0x5000, soundlatch2_w ),
		new Memory_WriteAddress( 0x6000, 0x6000, junofrst_i8039_irq_w ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	
	public static Memory_ReadAddress i8039_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x0fff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress i8039_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x0fff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static IO_ReadPort i8039_readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_ReadPort( 0x00, 0xff, soundlatch2_r ),
		new IO_ReadPort( 0x111,0x111, IORP_NOP ),
		new IO_ReadPort(MEMPORT_MARKER, 0)
	};
	
	public static IO_WritePort i8039_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_WritePort( I8039_p1, I8039_p1, DAC_0_data_w ),
		new IO_WritePort( I8039_p2, I8039_p2, i8039_irqen_and_status_w ),
		new IO_WritePort(MEMPORT_MARKER, 0)
	};
	
	
	static InputPortPtr input_ports_junofrst = new InputPortPtr(){ public void handler() { 
		PORT_START();       /* DSW2 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x03, "3" );
		PORT_DIPSETTING(    0x02, "4" );
		PORT_DIPSETTING(    0x01, "5" );
		PORT_BITX( 0,       0x00, IPT_DIPSWITCH_SETTING | IPF_CHEAT, "256", IP_KEY_NONE, IP_JOY_NONE );
		PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x04, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x70, 0x70, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x70, "1 (Easiest)" );
		PORT_DIPSETTING(    0x60, "2" );
		PORT_DIPSETTING(    0x50, "3" );
		PORT_DIPSETTING(    0x40, "4" );
		PORT_DIPSETTING(    0x30, "5" );
		PORT_DIPSETTING(    0x20, "6" );
		PORT_DIPSETTING(    0x10, "7" );
		PORT_DIPSETTING(    0x00, "8 (Hardest)" );
		PORT_DIPNAME( 0x80, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START();       /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_COIN1 );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_COIN2 );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_COIN3 );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_START1 );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_START2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();       /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON3 );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();       /* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_UP | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_COCKTAIL );
		PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON3 | IPF_COCKTAIL );
		PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
	
		PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x0f, 0x0f, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x02, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x08, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "3C_2C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "4C_3C") );
		PORT_DIPSETTING(    0x0f, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "3C_4C") );
		PORT_DIPSETTING(    0x07, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x0e, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "2C_5C") );
		PORT_DIPSETTING(    0x0d, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x0c, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x0b, DEF_STR( "1C_5C") );
		PORT_DIPSETTING(    0x0a, DEF_STR( "1C_6C") );
		PORT_DIPSETTING(    0x09, DEF_STR( "1C_7C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Free_Play") );
		PORT_DIPNAME( 0xf0, 0xf0, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x20, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x50, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "3C_2C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "4C_3C") );
		PORT_DIPSETTING(    0xf0, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "3C_4C") );
		PORT_DIPSETTING(    0x70, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0xe0, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x60, DEF_STR( "2C_5C") );
		PORT_DIPSETTING(    0xd0, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0xb0, DEF_STR( "1C_5C") );
		PORT_DIPSETTING(    0xa0, DEF_STR( "1C_6C") );
		PORT_DIPSETTING(    0x90, DEF_STR( "1C_7C") );
		PORT_DIPSETTING(    0x00, "Disabled" );
	/* 0x00 not remmed out since the game makes the usual sound if you insert the coin */
	INPUT_PORTS_END(); }}; 
	
	
	
	static AY8910interface ay8910_interface = new AY8910interface
	(
		1,	/* 1 chip */
		14318000/8,	/* 1.78975 MHz */
		new int[] { 30 },
		new ReadHandlerPtr[] { junofrst_portA_r },
		new ReadHandlerPtr[] { null },
		new WriteHandlerPtr[] { null },
		new WriteHandlerPtr[] { junofrst_portB_w }
	);
	
	static DACinterface dac_interface = new DACinterface
	(
		1,
		new int[] { 50 }
	);
	
	
	static MachineDriver machine_driver_junofrst = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6809,
				1500000,			/* 1.5 MHz ??? */
				readmem,writemem,null,null,
				interrupt,1
			),
			new MachineCPU(
				CPU_Z80 | CPU_AUDIO_CPU,
				14318000/8,	/* 1.78975 MHz */
				sound_readmem,sound_writemem,null,null,
				ignore_interrupt,1	/* interrupts are triggered by the main CPU */
			),
			new MachineCPU(
				CPU_I8039 | CPU_AUDIO_CPU,
				8000000/15,	/* 8MHz crystal */
				i8039_readmem,i8039_writemem,i8039_readport,i8039_writeport,
				ignore_interrupt,1
			)
		},
		30, DEFAULT_30HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* 1 CPU slice per frame - interleaving is forced when a sound command is written */
		null,				/* init machine routine */
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0*8, 32*8-1, 2*8, 30*8-1 ),	/* not sure about the visible area */
		null,					/* GfxDecodeInfo * */
		16,                                  /* total colors */
		0,                                      /* color table length */
		null,			/* convert color prom routine */
	
		VIDEO_TYPE_RASTER,
		null,						/* vh_init routine */
		generic_vh_start,					/* vh_start routine */
		generic_vh_stop,					/* vh_stop routine */
		tutankhm_vh_screenrefresh,				/* vh_update routine */
	
		/* sound hardware */
		0,0,0,0,
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
	
	
	static RomLoadPtr rom_junofrst = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 2*0x1c000, REGION_CPU1, 0 );/* code + space for decrypted opcodes */
		ROM_LOAD( "jfa_b9.bin",   0x0a000, 0x2000, 0xf5a7ab9d );/* program ROMs */
		ROM_LOAD( "jfb_b10.bin",  0x0c000, 0x2000, 0xf20626e0 );
		ROM_LOAD( "jfc_a10.bin",  0x0e000, 0x2000, 0x1e7744a7 );
	
		ROM_LOAD( "jfc1_a4.bin",  0x10000, 0x2000, 0x03ccbf1d );/* graphic and code ROMs (banked) */
		ROM_LOAD( "jfc2_a5.bin",  0x12000, 0x2000, 0xcb372372 );
		ROM_LOAD( "jfc3_a6.bin",  0x14000, 0x2000, 0x879d194b );
		ROM_LOAD( "jfc4_a7.bin",  0x16000, 0x2000, 0xf28af80b );
		ROM_LOAD( "jfc5_a8.bin",  0x18000, 0x2000, 0x0539f328 );
		ROM_LOAD( "jfc6_a9.bin",  0x1a000, 0x2000, 0x1da2ad6e );
	
		ROM_REGION(  0x10000 , REGION_CPU2, 0 );/* 64k for Z80 sound CPU code */
		ROM_LOAD( "jfs1_j3.bin",  0x0000, 0x1000, 0x235a2893 );
	
		ROM_REGION( 0x1000, REGION_CPU3, 0 );/* 8039 */
		ROM_LOAD( "jfs2_p4.bin",  0x0000, 0x1000, 0xd0fa5d5f );
	
		ROM_REGION( 0x6000, REGION_GFX1, 0 );/* BLTROM, used at runtime */
		ROM_LOAD( "jfs3_c7.bin",  0x00000, 0x2000, 0xaeacf6db );
		ROM_LOAD( "jfs4_d7.bin",  0x02000, 0x2000, 0x206d954c );
		ROM_LOAD( "jfs5_e7.bin",  0x04000, 0x2000, 0x1eb87a6e );
	ROM_END(); }}; 
	
	static RomLoadPtr rom_junofstg = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 2*0x1c000, REGION_CPU1, 0 );/* code + space for decrypted opcodes */
		ROM_LOAD( "jfg_a.9b",     0x0a000, 0x2000, 0x8f77d1c5 );/* program ROMs */
		ROM_LOAD( "jfg_b.10b",    0x0c000, 0x2000, 0xcd645673 );
		ROM_LOAD( "jfg_c.10a",    0x0e000, 0x2000, 0x47852761 );
	
		ROM_LOAD( "jfgc1.4a",     0x10000, 0x2000, 0x90a05ae6 );/* graphic and code ROMs (banked) */
		ROM_LOAD( "jfc2_a5.bin",  0x12000, 0x2000, 0xcb372372 );
		ROM_LOAD( "jfc3_a6.bin",  0x14000, 0x2000, 0x879d194b );
		ROM_LOAD( "jfgc4.7a",     0x16000, 0x2000, 0xe8864a43 );
		ROM_LOAD( "jfc5_a8.bin",  0x18000, 0x2000, 0x0539f328 );
		ROM_LOAD( "jfc6_a9.bin",  0x1a000, 0x2000, 0x1da2ad6e );
	
		ROM_REGION(  0x10000 , REGION_CPU2, 0 );/* 64k for Z80 sound CPU code */
		ROM_LOAD( "jfs1_j3.bin",  0x0000, 0x1000, 0x235a2893 );
	
		ROM_REGION( 0x1000, REGION_CPU3, 0 );/* 8039 */
		ROM_LOAD( "jfs2_p4.bin",  0x0000, 0x1000, 0xd0fa5d5f );
	
		ROM_REGION( 0x6000, REGION_GFX1, 0 );/* BLTROM, used at runtime */
		ROM_LOAD( "jfs3_c7.bin",  0x00000, 0x2000, 0xaeacf6db );
		ROM_LOAD( "jfs4_d7.bin",  0x02000, 0x2000, 0x206d954c );
		ROM_LOAD( "jfs5_e7.bin",  0x04000, 0x2000, 0x1eb87a6e );
	ROM_END(); }}; 
	
	
	
	public static InitDriverPtr init_junofrst = new InitDriverPtr() { public void handler()
	{
		konami1_decode();
	} };
	
	
	public static GameDriver driver_junofrst	   = new GameDriver("1983"	,"junofrst"	,"junofrst.java"	,rom_junofrst,null	,machine_driver_junofrst	,input_ports_junofrst	,init_junofrst	,ROT90	,	"Konami", "Juno First" );
	public static GameDriver driver_junofstg	   = new GameDriver("1983"	,"junofstg"	,"junofrst.java"	,rom_junofstg,driver_junofrst	,machine_driver_junofrst	,input_ports_junofrst	,init_junofrst	,ROT90	,	"Konami (Gottlieb license)", "Juno First (Gottlieb)" );
}
