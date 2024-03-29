/***************************************************************************

	BattleLane
	1986 Taito

	2x6809

    Driver provided by Paul Leaman (paul@vortexcomputing.demon.co.uk)

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP.mame056.vidhrdw.battlane.*;
import static WIP2.mame056.usrintrf.usrintf_showmessage;
import static WIP2.mame056.machine.ticket.*;
import static WIP2.mame056.machine.ticketH.*;

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

import static WIP2.mame056.sound._3526intf.*;
import static WIP2.mame056.sound._3812intfH.*;
import static WIP2.mame056.sound._2151intf.*;
import static WIP2.mame056.sound._2151intfH.*;
import static WIP2.mame056.sound._2203intf.*;
import static WIP2.mame056.sound._2203intfH.*;
import static WIP2.mame056.vidhrdw.generic.*;

import static WIP2.arcadeflex056.fileio.*;
import static WIP2.mame056.cpu.m6809.m6809H.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.sound._3812intfH.*;

public class battlane
{
	/* CPU interrupt control register */
	public static int battlane_cpu_control;
	
	/* RAM shared between CPU 0 and 1 */
	public static WriteHandlerPtr battlane_shared_ram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		UBytePtr RAM = new UBytePtr(memory_region(REGION_CPU1));
		RAM.write(offset, data);
	} };
	
	public static ReadHandlerPtr battlane_shared_ram_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		UBytePtr RAM = new UBytePtr(memory_region(REGION_CPU1));
		return RAM.read(offset);
	} };
	
	
	public static WriteHandlerPtr battlane_cpu_command_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		battlane_cpu_control=data;
	
		/*
		CPU control register
	
	        0x80    = Video Flip
	        0x08    = NMI
	        0x04    = CPU 0 IRQ   (0=Activate)
	        0x02    = CPU 1 IRQ   (0=Activate)
	        0x01    = Scroll MSB
		*/
	
	    battlane_set_video_flip(data & 0x80);
	
		/*
	        I think that the NMI is an inhibitor. It is constantly set
	        to zero whenever an NMIs are allowed.
	
	        However, it could also be that setting to zero could
	        cause the NMI to trigger. I really don't know.
		*/
	
	    /*
	    if (~battlane_cpu_control & 0x08)
	    {
	        cpu_set_nmi_line(0, PULSE_LINE);
	        cpu_set_nmi_line(1, PULSE_LINE);
	    }
	    */
	
		/*
	        CPU2's SWI will trigger an 6809 IRQ on the master by resetting 0x04
	        Master will respond by setting the bit back again
		*/
	    cpu_set_irq_line(0, M6809_IRQ_LINE,  (data & 0x04)!=0 ? CLEAR_LINE : HOLD_LINE);
	
		/*
		Slave function call (e.g. ROM test):
		FA7F: 86 03       LDA   #$03	; Function code
		FA81: 97 6B       STA   $6B
		FA83: 86 0E       LDA   #$0E
		FA85: 84 FD       ANDA  #$FD	; Trigger IRQ
		FA87: 97 66       STA   $66
		FA89: B7 1C 03    STA   $1C03	; Do Trigger
		FA8C: C6 40       LDB   #$40
		FA8E: D5 68       BITB  $68
		FA90: 27 FA       BEQ   $FA8C	; Wait for slave IRQ pre-function dispatch
		FA92: 96 68       LDA   $68
		FA94: 84 01       ANDA  #$01
		FA96: 27 FA       BEQ   $FA92	; Wait for bit to be set
		*/
	
		cpu_set_irq_line(1, M6809_IRQ_LINE,   (data & 0x02)!=0 ? CLEAR_LINE : HOLD_LINE);
	} };
	
	/* Both CPUs share the same memory */
	
	public static Memory_ReadAddress battlane_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x0fff, battlane_shared_ram_r ),
	    new Memory_ReadAddress( 0x1000, 0x17ff, battlane_tileram_r ),
	    new Memory_ReadAddress( 0x1800, 0x18ff, battlane_spriteram_r ),
		new Memory_ReadAddress( 0x1c00, 0x1c00, input_port_0_r ),
	    new Memory_ReadAddress( 0x1c01, 0x1c01, input_port_1_r ),   /* VBLANK port */
		new Memory_ReadAddress( 0x1c02, 0x1c02, input_port_2_r ),
		new Memory_ReadAddress( 0x1c03, 0x1c03, input_port_3_r ),
		new Memory_ReadAddress( 0x1c04, 0x1c04, YM3526_status_port_0_r ),
		new Memory_ReadAddress( 0x2000, 0x3fff, battlane_bitmap_r ),
		new Memory_ReadAddress( 0x4000, 0xffff, MRA_ROM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress battlane_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x0fff, battlane_shared_ram_w ),
	    new Memory_WriteAddress( 0x1000, 0x17ff, battlane_tileram_w ),
	    new Memory_WriteAddress( 0x1800, 0x18ff, battlane_spriteram_w ),
		new Memory_WriteAddress( 0x1c00, 0x1c00, battlane_video_ctrl_w ),
	    new Memory_WriteAddress( 0x1c01, 0x1c01, battlane_scrolly_w ),
	    new Memory_WriteAddress( 0x1c02, 0x1c02, battlane_scrollx_w ),
	    new Memory_WriteAddress( 0x1c03, 0x1c03, battlane_cpu_command_w ),
		new Memory_WriteAddress( 0x1c04, 0x1c04, YM3526_control_port_0_w ),
		new Memory_WriteAddress( 0x1c05, 0x1c05, YM3526_write_port_0_w ),
		new Memory_WriteAddress( 0x1e00, 0x1e3f, battlane_palette_w ),
		new Memory_WriteAddress( 0x2000, 0x3fff, battlane_bitmap_w, battlane_bitmap, battlane_bitmap_size ),
		new Memory_WriteAddress( 0x4000, 0xffff, MWA_ROM ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static InterruptPtr battlane_cpu1_interrupt = new InterruptPtr() { public int handler() 
	{
	/*TODO*///#ifdef MAME_DEBUG
	/*TODO*///	if (keyboard_pressed_memory(KEYCODE_F))
	/*TODO*///	{
	/*TODO*///		FILE *fp;
	/*TODO*///		fp=fopen("RAM.DMP", "w+b");
	/*TODO*///		if (fp)
	/*TODO*///		{
	/*TODO*///			unsigned char *RAM =
	/*TODO*///			memory_region(REGION_CPU1);
	/*TODO*///
	/*TODO*///			fwrite(RAM, 0x4000, 1, fp);
	/*TODO*///			fclose(fp);
	/*TODO*///		}
	/*TODO*///	}
	/*TODO*///#endif
	
		/* See note in battlane_cpu_command_w */
		if ((~battlane_cpu_control & 0x08) != 0)
		{
			cpu_set_nmi_line(1, PULSE_LINE);
			return M6809_INT_NMI;
		}
		else
			return ignore_interrupt.handler();
	} };
	
	
	
	static InputPortPtr input_ports_battlane = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON1 );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON2 );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_UP    | IPF_8WAY );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START1 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START2 );
	    PORT_START();       /* IN1 */
	    PORT_BIT( 0x7f, IP_ACTIVE_LOW, IPT_UNUSED );    /* Unused bits */
	    PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_VBLANK );    /* VBLank ? */
	
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
		PORT_DIPNAME( 0x10, 0x10, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x20, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0xc0, 0x80, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0xc0, "Easy"  );	PORT_DIPSETTING(    0x80, "Normal" );	PORT_DIPSETTING(    0x40, "Hard"  );	PORT_DIPSETTING(    0x00, "Very Hard" );
		PORT_START();       /* DSW2 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x03, "3" );	PORT_DIPSETTING(    0x02, "4" );	PORT_DIPSETTING(    0x01, "5" );	PORT_BITX(0,        0x00, IPT_DIPSWITCH_SETTING | IPF_CHEAT, "Infinite", IP_KEY_NONE, IP_JOY_NONE );	PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x0c, "20k & every 50k" );	PORT_DIPSETTING(    0x08, "20k & every 70k" );	PORT_DIPSETTING(    0x04, "20k & every 90k" );	PORT_DIPSETTING(    0x00, "None" );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_COIN1 );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_COIN2 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_COIN3 );INPUT_PORTS_END(); }}; 
	
	
	
	static GfxLayout spritelayout = new GfxLayout
	(
		16,16,
		RGN_FRAC(1,3),
		3,
		new int[] { RGN_FRAC(0,3), RGN_FRAC(1,3), RGN_FRAC(2,3) },
		new int[] {
			7, 6, 5, 4, 3, 2, 1, 0,
		  16*8+7, 16*8+6, 16*8+5, 16*8+4, 16*8+3, 16*8+2, 16*8+1, 16*8+0,
		},
		new int[] {
			15*8,14*8,13*8,12*8,11*8,10*8,9*8,8*8,
			7*8, 6*8, 5*8, 4*8, 3*8, 2*8, 1*8, 0*8,
		},
		16*8*2
	);
	
	static GfxLayout tilelayout = new GfxLayout
	(
		16,16 ,    /* 16*16 tiles */
		256,    /* 256 tiles */
		3,      /* 3 bits per pixel */
		new int[] { 0x8000*8+4, 4, 0 },    /* plane offset */
		new int[] {
		16+8+0, 16+8+1, 16+8+2, 16+8+3,
		16+0, 16+1, 16+2,   16+3,
		8+0,    8+1,    8+2,    8+3,
		0,       1,    2,      3,
		},
		new int[] { 0*8*4, 1*8*4,  2*8*4,  3*8*4,  4*8*4,  5*8*4,  6*8*4,  7*8*4,
		  8*8*4, 9*8*4, 10*8*4, 11*8*4, 12*8*4, 13*8*4, 14*8*4, 15*8*4
		},
		8*8*4*2     /* every char takes consecutive bytes */
	);
	
	static GfxLayout tilelayout2 = new GfxLayout
	(
		16,16 ,    /* 16*16 tiles */
		256,    /* 256 tiles */
		3,      /* 3 bits per pixel */
	    new int[] { 0x8000*8, 0x4000*8+4, 0x4000*8+0 },    /* plane offset */
		new int[] {
		16+8+0, 16+8+1, 16+8+2, 16+8+3,
		16+0, 16+1, 16+2,   16+3,
		8+0,    8+1,    8+2,    8+3,
		0,       1,    2,      3,
		},
		new int[] { 0*8*4, 1*8*4,  2*8*4,  3*8*4,  4*8*4,  5*8*4,  6*8*4,  7*8*4,
		  8*8*4, 9*8*4, 10*8*4, 11*8*4, 12*8*4, 13*8*4, 14*8*4, 15*8*4
		},
		8*8*4*2     /* every char takes consecutive bytes */
	);
	
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, spritelayout,  0, 2 ),	/* colors 0x00-0x0f */
		new GfxDecodeInfo( REGION_GFX2, 0, tilelayout,   32, 4 ),	/* colors 0x20-0x3f */
		new GfxDecodeInfo( REGION_GFX2, 0, tilelayout2,  32, 4 ),	/* colors 0x20-0x3f */
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	
	
	static WriteYmHandlerPtr irqhandler = new WriteYmHandlerPtr() {
            public void handler(int irq) {
                cpu_set_irq_line(0,M6809_FIRQ_LINE,irq!=0 ? ASSERT_LINE : CLEAR_LINE);
            }
        };
	
	static YM3526interface ym3526_interface = new YM3526interface
	(
		1,              /* 1 chip */
		3000000,        /* 3 MHz ??? */
		new int[]{ 100 },         /* volume */
		new WriteYmHandlerPtr[]{ irqhandler }
	);
	
	
	
	static MachineDriver machine_driver_battlane = new MachineDriver
	(
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6809,
				1250000,        /* 1.25 MHz ? */
				battlane_readmem, battlane_writemem,null,null,
				battlane_cpu1_interrupt,1
			),
			new MachineCPU(
				CPU_M6809,
				1250000,        /* 1.25 MHz ? */
				battlane_readmem, battlane_writemem,null,null,
				ignore_interrupt,0	/* interrupts are generated by CPU1 */
			)
		},
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,  /* frames per second, vblank duration */
		100,      /* CPU slices per frame */
		null,      /* init machine */
	
		/* video hardware */
		32*8, 32*8, new rectangle( 0, 32*8-1, 0, 32*8-1 ),       /* not sure */
		gfxdecodeinfo,
		64, 0,
		null,
	
		VIDEO_TYPE_RASTER,
		null ,
		battlane_vh_start,
		battlane_vh_stop,
		battlane_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_YM3526,
				ym3526_interface
			)
		}
	);
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	static RomLoadPtr rom_battlane = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );    /* 64k for main CPU */
		/* first half of da00-5 will be copied at 0x4000-0x7fff */
		ROM_LOAD( "da01-5",    0x8000, 0x8000, 0x7a6c3f02 );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );    /* 64K for slave CPU */
		ROM_LOAD( "da00-5",    0x00000, 0x8000, 0x85b4ed73 );/* ...second half goes here */
		ROM_LOAD( "da02-2",    0x08000, 0x8000, 0x69d8dafe );
		ROM_REGION( 0x18000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "da05",      0x00000, 0x8000, 0x834829d4 );/* Sprites Plane 1+2 */
		ROM_LOAD( "da04",      0x08000, 0x8000, 0xf083fd4c );/* Sprites Plane 3+4 */
		ROM_LOAD( "da03",      0x10000, 0x8000, 0xcf187f25 );/* Sprites Plane 5+6 */
	
		ROM_REGION( 0x0c000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "da06",      0x00000, 0x8000, 0x9c6a51b3 );/* Tiles*/
		ROM_LOAD( "da07",      0x08000, 0x4000, 0x56df4077 );/* Tiles*/
	
		ROM_REGION( 0x0040, REGION_PROMS, 0 );	ROM_LOAD( "82s123.7h", 0x00000, 0x0020, 0xb9933663 );	ROM_LOAD( "82s123.9n", 0x00020, 0x0020, 0x06491e53 );ROM_END(); }}; 
	
	static RomLoadPtr rom_battlan2 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );    /* 64k for main CPU */
		/* first half of da00-3 will be copied at 0x4000-0x7fff */
		ROM_LOAD( "da01-3",    0x8000, 0x8000, 0xd9e40800 );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );    /* 64K for slave CPU */
		ROM_LOAD( "da00-3",    0x00000, 0x8000, 0x7a0a5d58 );/* ...second half goes here */
		ROM_LOAD( "da02-2",    0x08000, 0x8000, 0x69d8dafe );
		ROM_REGION( 0x18000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "da05",      0x00000, 0x8000, 0x834829d4 );/* Sprites Plane 1+2 */
		ROM_LOAD( "da04",      0x08000, 0x8000, 0xf083fd4c );/* Sprites Plane 3+4 */
		ROM_LOAD( "da03",      0x10000, 0x8000, 0xcf187f25 );/* Sprites Plane 5+6 */
	
		ROM_REGION( 0x0c000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "da06",      0x00000, 0x8000, 0x9c6a51b3 );/* Tiles*/
		ROM_LOAD( "da07",      0x08000, 0x4000, 0x56df4077 );/* Tiles*/
	
		ROM_REGION( 0x0040, REGION_PROMS, 0 );	ROM_LOAD( "82s123.7h", 0x00000, 0x0020, 0xb9933663 );	ROM_LOAD( "82s123.9n", 0x00020, 0x0020, 0x06491e53 );ROM_END(); }}; 
	
	static RomLoadPtr rom_battlan3 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );    /* 64k for main CPU */
		/* first half of bl_04.rom will be copied at 0x4000-0x7fff */
		ROM_LOAD( "bl_05.rom", 0x8000, 0x8000, 0x001c4bbe );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );    /* 64K for slave CPU */
		ROM_LOAD( "bl_04.rom", 0x00000, 0x8000, 0x5681564c );/* ...second half goes here */
		ROM_LOAD( "da02-2",    0x08000, 0x8000, 0x69d8dafe );
		ROM_REGION( 0x18000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "da05",      0x00000, 0x8000, 0x834829d4 );/* Sprites Plane 1+2 */
		ROM_LOAD( "da04",      0x08000, 0x8000, 0xf083fd4c );/* Sprites Plane 3+4 */
		ROM_LOAD( "da03",      0x10000, 0x8000, 0xcf187f25 );/* Sprites Plane 5+6 */
	
		ROM_REGION( 0x0c000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "da06",      0x00000, 0x8000, 0x9c6a51b3 );/* Tiles*/
		ROM_LOAD( "da07",      0x08000, 0x4000, 0x56df4077 );/* Tiles*/
	
		ROM_REGION( 0x0040, REGION_PROMS, 0 );	ROM_LOAD( "82s123.7h", 0x00000, 0x0020, 0xb9933663 );	ROM_LOAD( "82s123.9n", 0x00020, 0x0020, 0x06491e53 );ROM_END(); }}; 
	
	
	
	public static InitDriverPtr init_battlane = new InitDriverPtr() { public void handler()
	{
		UBytePtr src=new UBytePtr(), dest=new UBytePtr();
		int A;
	
		/* one ROM is shared among two CPUs. We loaded it into the */
		/* second CPU address space, let's copy it to the first CPU's one */
		src = new UBytePtr(memory_region(REGION_CPU2));
		dest = new UBytePtr(memory_region(REGION_CPU1));
	
		for(A = 0;A < 0x4000;A++)
			dest.write(A + 0x4000, src.read(A));
	} };
	
	
	
	public static GameDriver driver_battlane	   = new GameDriver("1986"	,"battlane"	,"battlane.java"	,rom_battlane,null	,machine_driver_battlane	,input_ports_battlane	,init_battlane	,ROT90	,	"Technos (Taito license)", "Battle Lane Vol. 5 (set 1)", GAME_NO_COCKTAIL );
	public static GameDriver driver_battlan2	   = new GameDriver("1986"	,"battlan2"	,"battlane.java"	,rom_battlan2,driver_battlane	,machine_driver_battlane	,input_ports_battlane	,init_battlane	,ROT90	,	"Technos (Taito license)", "Battle Lane Vol. 5 (set 2)", GAME_NO_COCKTAIL );
	public static GameDriver driver_battlan3	   = new GameDriver("1986"	,"battlan3"	,"battlane.java"	,rom_battlan3,driver_battlane	,machine_driver_battlane	,input_ports_battlane	,init_battlane	,ROT90	,	"Technos (Taito license)", "Battle Lane Vol. 5 (set 3)", GAME_NO_COCKTAIL );
}
