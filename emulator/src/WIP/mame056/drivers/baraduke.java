/***************************************************************************

Baraduke/Metro-Cross (c) Namco 1985

Driver by Manuel Abadia <manu@teleline.es>

TO DO:
	- in Metro-Cross test mode, inputs are unnecessarily repeated, so
	selecting the sound you want to listen to is almost impossible.
	- remove the sound kludge in Baraduke.

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
import static WIP2.mame056.cpuexec.*;
import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.memory.*;
import static WIP2.mame056.drawgfxH.*;
import static WIP2.mame056.driverH.*;
import static WIP2.mame056.inptport.*;
import static WIP2.mame056.inptportH.*;
import static WIP2.mame056.memoryH.*;
import static WIP2.common.libc.cstring.*;
import static WIP2.mame056.palette.*;
import static WIP2.mame056.sndintrf.*;
import static WIP2.mame056.sndintrfH.*;
import static WIP.mame056.vidhrdw.baraduke.*;
import static WIP2.mame056.vidhrdw.generic.*;
// refactor
import static WIP2.arcadeflex036.osdepend.logerror;
import static WIP2.mame056.cpu.m6800.hd63701.*;
import static WIP2.mame056.cpu.m6800.m6800H.*;
import static WIP2.mame056.mame.*;
import static WIP2.mame056.inputH.*;
import static WIP2.mame056.sound.namco.*;
import static WIP2.mame056.sound.namcoH.*;


public class baraduke
{
	
	static UBytePtr sharedram = new UBytePtr();
	
	static int inputport_selected;
	
	public static WriteHandlerPtr inputport_select_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if ((data & 0xe0) == 0x60)
			inputport_selected = data & 0x07;
		else if ((data & 0xe0) == 0xc0)
		{
			coin_lockout_global_w(~data & 1);
			coin_counter_w.handler(0,data & 2);
			coin_counter_w.handler(1,data & 4);
		}
	} };
	
	public static int reverse_bitstrm(int data){
            return ((data & 0x01) << 4) | ((data & 0x02) << 2) | (data & 0x04)
								| ((data & 0x08) >> 2) | ((data & 0x10) >> 4);
        }
	
	public static ReadHandlerPtr inputport_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int data = 0;
	
		switch (inputport_selected){
			case 0x00:	/* DSW A (bits 0-4) */
				data = ~(reverse_bitstrm(readinputport(0) & 0x1f)); break;
			case 0x01:	/* DSW A (bits 5-7), DSW B (bits 0-1) */
				data = ~(reverse_bitstrm((((readinputport(0) & 0xe0) >> 5) | ((readinputport(1) & 0x03) << 3)))); break;
			case 0x02:	/* DSW B (bits 2-6) */
				data = ~(reverse_bitstrm(((readinputport(1) & 0x7c) >> 2))); break;
			case 0x03:	/* DSW B (bit 7), DSW C (bits 0-3) */
				data = ~(reverse_bitstrm((((readinputport(1) & 0x80) >> 7) | ((readinputport(2) & 0x0f) << 1)))); break;
			case 0x04:	/* coins, start */
				data = ~(readinputport(3)); break;
			case 0x05:	/* 2P controls */
				data = ~(readinputport(5)); break;
			case 0x06:	/* 1P controls */
				data = ~(readinputport(4)); break;
			default:
				data = 0xff;
		}
	
		return data;
	} };
	
	public static WriteHandlerPtr baraduke_lamps_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		set_led_status(0,data & 0x08);
		set_led_status(1,data & 0x10);
	} };
	
	public static ReadHandlerPtr baraduke_sharedram_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return sharedram.read(offset);
	} };
	public static WriteHandlerPtr baraduke_sharedram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		sharedram.write(offset, data);
	} };
	
	public static Memory_ReadAddress baraduke_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x17ff, MRA_RAM ),				/* RAM */
		new Memory_ReadAddress( 0x1800, 0x1fff, MRA_RAM ),				/* Sprite RAM */
		new Memory_ReadAddress( 0x2000, 0x3fff, baraduke_videoram_r ),	/* Video RAM */
		new Memory_ReadAddress( 0x4000, 0x40ff, namcos1_wavedata_r ),		/* PSG device, shared RAM */
		new Memory_ReadAddress( 0x4000, 0x43ff, baraduke_sharedram_r ),	/* shared RAM with the MCU */
		new Memory_ReadAddress( 0x4800, 0x4fff, MRA_RAM ),				/* video RAM (text layer) */
		new Memory_ReadAddress( 0x6000, 0xffff, MRA_ROM ),				/* ROM */
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress baraduke_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x17ff, MWA_RAM ),				/* RAM */
		new Memory_WriteAddress( 0x1800, 0x1fff, MWA_RAM, spriteram ),	/* Sprite RAM */
		new Memory_WriteAddress( 0x2000, 0x3fff, baraduke_videoram_w, baraduke_videoram ),/* Video RAM */
		new Memory_WriteAddress( 0x4000, 0x40ff, namcos1_wavedata_w ),		/* PSG device, shared RAM */
		new Memory_WriteAddress( 0x4000, 0x43ff, baraduke_sharedram_w, sharedram ),/* shared RAM with the MCU */
		new Memory_WriteAddress( 0x4800, 0x4fff, MWA_RAM, baraduke_textram ),/* video RAM (text layer) */
		new Memory_WriteAddress( 0x8000, 0x8000, watchdog_reset_w ),		/* watchdog reset */
	//	new Memory_WriteAddress( 0x8800, 0x8800, MWA_NOP ),				/* ??? */
		new Memory_WriteAddress( 0xb000, 0xb002, baraduke_scroll0_w ),		/* scroll (layer 0) */
		new Memory_WriteAddress( 0xb004, 0xb006, baraduke_scroll1_w ),		/* scroll (layer 1) */
		new Memory_WriteAddress( 0x6000, 0xffff, MWA_ROM ),				/* ROM */
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
        
        static int counter;
	
	public static ReadHandlerPtr soundkludge_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return ((counter++) >> 4) & 0xff;
	} };
	
	public static Memory_ReadAddress mcu_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_ReadAddress( 0x0000, 0x001f, hd63701_internal_registers_r ),/* internal registers */
		new Memory_ReadAddress( 0x0080, 0x00ff, MRA_RAM ),					/* built in RAM */
		new Memory_ReadAddress( 0x1000, 0x10ff, namcos1_wavedata_r ),			/* PSG device, shared RAM */
		new Memory_ReadAddress( 0x1105, 0x1105, soundkludge_r ),				/* cures speech */
		new Memory_ReadAddress( 0x1100, 0x113f, MRA_RAM ),					/* PSG device */
		new Memory_ReadAddress( 0x1000, 0x13ff, baraduke_sharedram_r ),		/* shared RAM with the 6809 */
		new Memory_ReadAddress( 0x8000, 0xbfff, MRA_ROM ),					/* MCU external ROM */
		new Memory_ReadAddress( 0xc000, 0xc800, MRA_RAM ),					/* RAM */
		new Memory_ReadAddress( 0xf000, 0xffff, MRA_ROM ),					/* MCU internal ROM */
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	
	public static Memory_WriteAddress mcu_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),
		new Memory_WriteAddress( 0x0000, 0x001f, hd63701_internal_registers_w ),/* internal registers */
		new Memory_WriteAddress( 0x0080, 0x00ff, MWA_RAM ),				/* built in RAM */
		new Memory_WriteAddress( 0x1000, 0x10ff, namcos1_wavedata_w, namco_wavedata ),/* PSG device, shared RAM */
		new Memory_WriteAddress( 0x1100, 0x113f, namcos1_sound_w, namco_soundregs ),/* PSG device */
		new Memory_WriteAddress( 0x1000, 0x13ff, baraduke_sharedram_w ),	/* shared RAM with the 6809 */
	//	new Memory_WriteAddress( 0x8000, 0x8000, MWA_NOP ),				/* ??? */
	//	new Memory_WriteAddress( 0x8800, 0x8800, MWA_NOP ),				/* ??? */
		new Memory_WriteAddress( 0x8000, 0xbfff, MWA_ROM ),				/* MCU external ROM */
		new Memory_WriteAddress( 0xc000, 0xc800, MWA_RAM ),				/* RAM */
		new Memory_WriteAddress( 0xf000, 0xffff, MWA_ROM ),				/* MCU internal ROM */
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static IO_ReadPort mcu_readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_ReadPort( HD63701_PORT1, HD63701_PORT1, inputport_r ),			/* input ports read */
		new IO_ReadPort(MEMPORT_MARKER, 0)
	};
	
	public static IO_WritePort mcu_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),
		new IO_WritePort( HD63701_PORT1, HD63701_PORT1, inputport_select_w ),	/* input port select */
		new IO_WritePort( HD63701_PORT2, HD63701_PORT2, baraduke_lamps_w ),		/* lamps */
		new IO_WritePort(MEMPORT_MARKER, 0)
	};
	
	static InputPortPtr input_ports_baraduke = new InputPortPtr(){ public void handler() { 
		PORT_START(); 	/* DSW A */
		PORT_SERVICE( 0x01, IP_ACTIVE_HIGH );
		PORT_DIPNAME( 0x06, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x04, "2" );
		PORT_DIPSETTING(    0x00, "3" );
		PORT_DIPSETTING(    0x02, "4" );
		PORT_DIPSETTING(    0x06, "5" );
		PORT_DIPNAME( 0x18, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x18, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x08, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x20, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0xc0, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "1C_2C") );
	
		PORT_START(); 	/* DSW B */
		PORT_DIPNAME( 0x03, 0x00, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x02, "Every 10k" );
		PORT_DIPSETTING(    0x00, "10k and every 20k" );
		PORT_DIPSETTING(    0x01, "Every 20k" );
		PORT_DIPSETTING(    0x03, "None" );
		PORT_DIPNAME( 0x0c, 0x00, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x08, "Easy" );
		PORT_DIPSETTING(    0x00, "Normal" );
		PORT_DIPSETTING(    0x04, "Hard" );
		PORT_DIPSETTING(    0x0c, "Very hard" );
		PORT_BITX(    0x10, 0x00, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Rack test", IP_KEY_NONE, IP_JOY_NONE );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, "Freeze" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x20, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, "Allow continue from last level" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x40, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x80, DEF_STR( "On") );
	
		PORT_START(); 	/* DSW C */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x01, DEF_STR( "On") );
		PORT_DIPNAME( 0x02, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x02, DEF_STR( "On") );
		PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x04, DEF_STR( "Cocktail") );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_SERVICE );/* Another service dip */
		PORT_BIT( 0xf0, IP_ACTIVE_HIGH, IPT_UNUSED );
	
		PORT_START(); 	/* IN 0 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_SERVICE1 );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_COIN1 );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_COIN2 );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_START1 );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_START2 );
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNUSED );
	
		PORT_START(); 	/* IN 1 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT  | IPF_8WAY );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN  | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP    | IPF_8WAY );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_PLAYER1 );
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNUSED );
	
		PORT_START(); 	/* IN 2 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT  | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN  | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP    | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNUSED );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_metrocrs = new InputPortPtr(){ public void handler() { 
		PORT_START(); 	/* DSW A */
		PORT_SERVICE( 0x01, IP_ACTIVE_HIGH );
		PORT_DIPNAME( 0x06, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x06, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x18, 0x00, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x10, "Easy" );
		PORT_DIPSETTING(    0x00, "Normal" );
		PORT_DIPSETTING(    0x08, "Hard" );
		PORT_DIPSETTING(    0x18, "Very hard" );
		PORT_DIPNAME( 0x20, 0x00, "Allow Continue" );
		PORT_DIPSETTING(    0x20, DEF_STR( "No") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Yes") );
		PORT_DIPNAME( 0xc0, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "1C_2C") );
	
		PORT_START(); 	/* DSW B */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Demo_Sounds") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_BITX(    0x02, 0x00, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Rack test", IP_KEY_NONE, IP_JOY_NONE );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x02, DEF_STR( "On") );
		PORT_DIPNAME( 0x04, 0x00, "Freeze" );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x08, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x10, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x20, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x40, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x80, DEF_STR( "On") );
	
		PORT_START(); 	/* DSW C */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x01, DEF_STR( "On") );
		PORT_DIPNAME( 0x02, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x02, DEF_STR( "On") );
		PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x04, DEF_STR( "Cocktail") );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_SERVICE );/* Another service dip */
		PORT_BIT( 0xf0, IP_ACTIVE_HIGH, IPT_UNUSED );
	
		PORT_START(); 	/* IN 0 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_SERVICE1 );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_COIN1 );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_COIN2 );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_START1 );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_START2 );
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNUSED );
	
		PORT_START(); 	/* IN 1 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT  | IPF_8WAY );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN  | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP    | IPF_8WAY );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_PLAYER1 );
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNUSED );
	
		PORT_START(); 	/* IN 2 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT  | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN  | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP    | IPF_8WAY | IPF_PLAYER2 );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_PLAYER2 );
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNUSED );
		PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNUSED );
	INPUT_PORTS_END(); }}; 
	
	
	
	static GfxLayout text_layout = new GfxLayout
	(
		8,8,		/* 8*8 characters */
		512,		/* 512 characters */
		2,			/* 2 bits per pixel */
		new int[] { 0, 4 },	/* the bitplanes are packed in the same byte */
		new int[] { 8*8, 8*8+1, 8*8+2, 8*8+3, 0, 1, 2, 3 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		16*8		/* every char takes 16 consecutive bytes */
	);
	
	static GfxLayout tile_layout1 = new GfxLayout
	(
		8,8,		/* 8*8 characters */
		512,		/* 512 characters */
		3,			/* 3 bits per pixel */
		new int[] { 0x8000*8, 0, 4 },
		new int[] { 0, 1, 2, 3, 8+0, 8+1, 8+2, 8+3 },
		new int[] { 0*8, 2*8, 4*8, 6*8, 8*8, 10*8, 12*8, 14*8 },
		16*8		/* every char takes 16 consecutive bytes */
	);
	
	static GfxLayout tile_layout2 = new GfxLayout
	(
		8,8,		/* 8*8 characters */
		512,		/* 512 characters */
		3,			/* 3 bits per pixel */
		new int[] { 0x8000*8+4, 0x2000*8, 0x2000*8+4 },
		new int[] { 0, 1, 2, 3, 8+0, 8+1, 8+2, 8+3 },
		new int[] { 0*8, 2*8, 4*8, 6*8, 8*8, 10*8, 12*8, 14*8 },
		16*8		/* every char takes 16 consecutive bytes */
	);
	
	static GfxLayout tile_layout3 = new GfxLayout
	(
		8,8,		/* 8*8 characters */
		512,		/* 512 characters */
		3,			/* 3 bits per pixel */
		new int[] { 0xa000*8, 0x4000*8, 0x4000*8+4 },
		new int[] { 0, 1, 2, 3, 8+0, 8+1, 8+2, 8+3 },
		new int[] { 0*8, 2*8, 4*8, 6*8, 8*8, 10*8, 12*8, 14*8 },
		16*8		/* every char takes 16 consecutive bytes */
	);
	
	static GfxLayout tile_layout4 = new GfxLayout
	(
		8,8,		/* 8*8 characters */
		512,		/* 512 characters */
		3,			/* 3 bits per pixel */
		new int[] { 0xa000*8+4, 0x6000*8, 0x6000*8+4 },
		new int[] { 0, 1, 2, 3, 8+0, 8+1, 8+2, 8+3 },
		new int[] { 0*8, 2*8, 4*8, 6*8, 8*8, 10*8, 12*8, 14*8 },
		16*8		/* every char takes 16 consecutive bytes */
	);
	
	static GfxLayout spritelayout = new GfxLayout
	(
		16,16,		/* 16*16 sprites */
		512,		/* 512 sprites */
		4,			/* 4 bits per pixel */
		new int[] { 0, 1, 2, 3 },
		new int[] { 0*4, 1*4, 2*4, 3*4, 4*4, 5*4, 6*4, 7*4,
			8*4, 9*4, 10*4, 11*4, 12*4, 13*4, 14*4, 15*4 },
	    new int[] { 8*8*0, 8*8*1, 8*8*2, 8*8*3, 8*8*4, 8*8*5, 8*8*6, 8*8*7,
		8*8*8, 8*8*9, 8*8*10, 8*8*11, 8*8*12, 8*8*13, 8*8*14, 8*8*15 },
		128*8		/* every sprite takes 128 consecutive bytes */
	);
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, text_layout,		0, 512 ),
		new GfxDecodeInfo( REGION_GFX2, 0, tile_layout1,	0, 256 ),
		new GfxDecodeInfo( REGION_GFX2, 0, tile_layout2,	0, 256 ),
		new GfxDecodeInfo( REGION_GFX2, 0, tile_layout3,	0, 256 ),
		new GfxDecodeInfo( REGION_GFX2, 0, tile_layout4,	0, 256 ),
		new GfxDecodeInfo( REGION_GFX3, 0, spritelayout,	0, 128 ),
		new GfxDecodeInfo( -1 )
	};
	
	static namco_interface namco_interface = new namco_interface
	(
		49152000/2048, 		/* 24000Hz */
		8,					/* number of voices */
		100,				/* playback volume */
		-1,					/* memory region */
		0					/* stereo */
	);
	
	
	static MachineDriver machine_driver_baraduke = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6809,
				49152000/32,	/* ??? */
				baraduke_readmem,baraduke_writemem,null,null,
				interrupt,1
			),
			new MachineCPU(
				CPU_HD63701,	/* or compatible 6808 with extra instructions */
				49152000/32,	/* ??? */
				mcu_readmem,mcu_writemem,mcu_readport,mcu_writeport,
				interrupt,1
			)
		},
		60.606060f,DEFAULT_REAL_60HZ_VBLANK_DURATION,
		100,		/* we need heavy synch */
		null,
	
		/* video hardware */
		36*8, 28*8, new rectangle( 0*8, 36*8-1, 0*8, 28*8-1 ),
		gfxdecodeinfo,
		2048,2048*4,
		baraduke_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,	/* palette is static but doesn't fit in 256 colors */
		null,
		baraduke_vh_start,
		null,
		baraduke_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_NAMCO,
				namco_interface
			)
		}
	);
	
	static MachineDriver machine_driver_metrocrs = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_M6809,
				49152000/32,	/* ??? */
				baraduke_readmem,baraduke_writemem,null,null,
				interrupt,1
			),
			new MachineCPU(
				CPU_HD63701,	/* or compatible 6808 with extra instructions */
				49152000/32,	/* ??? */
				mcu_readmem,mcu_writemem,mcu_readport,mcu_writeport,
				interrupt,1
			)
		},
		60.606060f,DEFAULT_REAL_60HZ_VBLANK_DURATION,
		100,		/* we need heavy synch */
		null,
	
		/* video hardware */
		36*8, 28*8, new rectangle( 0*8, 36*8-1, 0*8, 28*8-1 ),
		gfxdecodeinfo,
		2048,2048*4,
		baraduke_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		baraduke_vh_start,
		null,
		metrocrs_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_NAMCO,
				namco_interface
			)
		}
	);
	
	static RomLoadPtr rom_baraduke = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 6809 code */
		ROM_LOAD( "prg1.9c",	0x6000, 0x02000, 0xea2ea790 );
		ROM_LOAD( "prg2.9a",	0x8000, 0x04000, 0x9a0a9a87 );
		ROM_LOAD( "prg3.9b",	0xc000, 0x04000, 0x383e5458 );
	
		ROM_REGION(  0x10000 , REGION_CPU2, 0 );/* MCU code */
		ROM_LOAD( "prg4.3b",	0x8000,  0x4000, 0xabda0fe7 );/* subprogram for the MCU */
		ROM_LOAD( "pl1-mcu.bin",0xf000,	 0x1000, 0x6ef08fb3 );/* The MCU internal code is missing */
																/* Using Pacland code (probably similar) */
		ROM_REGION( 0x02000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "ch1.3j",		0x00000, 0x2000, 0x706b7fee );/* characters */
	
		ROM_REGION( 0x0c000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "ch2.4p",		0x00000, 0x4000, 0xb0bb0710 );/* tiles */
		ROM_LOAD( "ch3.4n",		0x04000, 0x4000, 0x0d7ebec9 );
		ROM_LOAD( "ch4.4m",		0x08000, 0x4000, 0xe5da0896 );
	
		ROM_REGION( 0x10000, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "obj1.8k",	0x00000, 0x4000, 0x87a29acc );/* sprites */
		ROM_LOAD( "obj2.8l",	0x04000, 0x4000, 0x72b6d20c );
		ROM_LOAD( "obj3.8m",	0x08000, 0x4000, 0x3076af9c );
		ROM_LOAD( "obj4.8n",	0x0c000, 0x4000, 0x8b4c09a3 );
	
		ROM_REGION( 0x1000, REGION_PROMS, 0 );
		ROM_LOAD( "prmcolbg.1n",0x0000, 0x0800, 0x0d78ebc6 );/* Blue + Green palette */
		ROM_LOAD( "prmcolr.2m",	0x0800, 0x0800, 0x03f7241f );/* Red palette */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_metrocrs = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 6809 code */
		ROM_LOAD( "mc1-3.9c",	0x6000, 0x02000, 0x3390b33c );
		ROM_LOAD( "mc1-1.9a",	0x8000, 0x04000, 0x10b0977e );
		ROM_LOAD( "mc1-2.9b",	0xc000, 0x04000, 0x5c846f35 );
	
		ROM_REGION(  0x10000 , REGION_CPU2, 0 );/* MCU code */
		ROM_LOAD( "mc1-4.3b",	0x8000, 0x02000, 0x9c88f898 );/* subprogram for the MCU */
		ROM_LOAD( "pl1-mcu.bin",0xf000,	 0x1000, 0x6ef08fb3 );/* The MCU internal code is missing */
																/* Using Pacland code (probably similar) */
		ROM_REGION( 0x02000, REGION_GFX1, ROMREGION_DISPOSE );
		ROM_LOAD( "mc1-5.3j",	0x00000, 0x2000, 0x9b5ea33a );/* characters */
	
		ROM_REGION( 0x0c000, REGION_GFX2, ROMREGION_DISPOSE );
		ROM_LOAD( "mc1-7.4p",	0x00000, 0x4000, 0xc9dfa003 );/* tiles */
		ROM_LOAD( "mc1-6.4n",	0x04000, 0x4000, 0x9686dc3c );
		/* empty space to decode the roms as 3bpp */
	
		ROM_REGION( 0x10000, REGION_GFX3, ROMREGION_DISPOSE );
		ROM_LOAD( "mc1-8.8k",	0x00000, 0x4000, 0x265b31fa );/* sprites */
		ROM_LOAD( "mc1-9.8l",	0x04000, 0x4000, 0x541ec029 );
		/* 8000-ffff empty */
	
		ROM_REGION( 0x1000, REGION_PROMS, 0 );
		ROM_LOAD( "mc1-1.1n",	0x0000, 0x0800, 0x32a78a8b );/* Blue + Green palette */
		ROM_LOAD( "mc1-2.2m",	0x0800, 0x0800, 0x6f4dca7b );/* Red palette */
	ROM_END(); }}; 
	
	
	
	public static InitDriverPtr init_metrocrs = new InitDriverPtr() { public void handler()
	{
		int i;
		UBytePtr rom = new UBytePtr(memory_region(REGION_GFX2));
	
		for(i = 0x8000;i < memory_region_length(REGION_GFX2);i++)
			rom.write(i, 0xff);
	} };
	
	
	
	public static GameDriver driver_baraduke	   = new GameDriver("1985"	,"baraduke"	,"baraduke.java"	,rom_baraduke,null	,machine_driver_baraduke	,input_ports_baraduke	,null	,ROT0	,	"Namco", "Baraduke" );
	public static GameDriver driver_metrocrs	   = new GameDriver("1985"	,"metrocrs"	,"baraduke.java"	,rom_metrocrs,null	,machine_driver_metrocrs	,input_ports_metrocrs	,init_metrocrs	,ROT0	,	"Namco", "Metro-Cross" );
}
