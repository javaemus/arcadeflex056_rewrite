/***************************************************************************

Universal board numbers (found on the schematics)

Cosmic Guerilla - 7907A
Cosmic Alien    - 7910
Magical Spot II - 8013
Devil Zone      - 8022


Space Panic memory map

driver by Mike Coates

0000-3FFF ROM
4000-5BFF Video RAM (Bitmap)
5C00-5FFF RAM
6000-601F Sprite Controller
          4 bytes per sprite

          byte 1 - 80 = ?
                   40 = Rotate sprite left/right
                   3F = Sprite Number (Conversion to my layout via table)

          byte 2 - X co-ordinate
          byte 3 - Y co-ordinate

          byte 4 - 08 = Switch sprite bank
                   07 = Colour

6800      IN1 - Player controls. See input port setup for mappings
6801      IN2 - Player 2 controls for cocktail mode. See input port setup for mappings.
6802      DSW - Dip switches
6803      IN0 - Coinage and player start
700C-700E Colour Map Selector

(Not Implemented)

7000-700B Various triggers, Sound etc
700F      Ditto
7800      80 = Flash Screen?

***************************************************************************/

/***************************************************************************

Magical Spot 2 memory map (preliminary)

0000-2fff ROM
6000-63ff RAM
6400-7fff Video RAM

read:

write:

***************************************************************************/

/*************************************************************************/
/* Cosmic Guerilla memory map	                                         */
/*************************************************************************/
/* 0000-03FF   ROM                          COSMICG.1                    */
/* 0400-07FF   ROM                          COSMICG.2                    */
/* 0800-0BFF   ROM                          COSMICG.3                    */
/* 0C00-0FFF   ROM                          COSMICG.4                    */
/* 1000-13FF   ROM                          COSMICG.5                    */
/* 1400-17FF   ROM                          COSMICG.6                    */
/* 1800-1BFF   ROM                          COSMICG.7                    */
/* 1C00-1FFF   ROM                          COSMICG.8 - Graphics         */
/*                                                                       */
/* 2000-23FF   RAM                                                       */
/* 2400-3FEF   Screen RAM                                                */
/* 3FF0-3FFF   CRT Controller registers (3FF0, register, 3FF4 Data)      */
/*                                                                       */
/* CRTC data                                                             */
/*             ROM                          COSMICG.9 - Color Prom       */
/*                                                                       */
/* CR Bits (Inputs)                                                      */
/* 0000-0003   A9-A13 from CRTC Pixel Clock                              */
/* 0004-000B   Controls                                                  */
/* 000C-000F   Dip Switches                                              */
/*                                                                       */
/* CR Bits (Outputs)                                                     */
/* 0016-0017   Colourmap Selector                                        */
/*************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP2.arcadeflex056.fucPtr.*;
import WIP2.common.ptr.UBytePtr;

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
import static WIP2.mame056.sound.dac.*;
import static WIP2.mame056.sound.dacH.*;

import static WIP2.mame056.vidhrdw.generic.*;

import static WIP2.arcadeflex056.fileio.*;
import static mame056.palette.*;
import static WIP2.mame056.inptport.*;

import static WIP.mame056.vidhrdw.cosmic.*;

public class cosmic
{
        /* R Nabet : One weird thing is that the memory map allows the use of a cheaper tms9980.
        Did the original hardware really use the high-end tms9900 ? */
        /* Set the flag below to compile with a tms9980. */

        public static int COSMICG_USES_TMS9980 = 1;
	
	
	static int pixel_clock = 0;
        
        static int sound_enabled=1;
	
	
	/* Schematics show 12 triggers for discrete sound circuits */
	
	public static WriteHandlerPtr panic_sound_output_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
	    
	
	    /* Sound Enable / Disable */
	
	    if (offset == 11)
	    {
	    	int count;
	    	if (data == 0)
	        	for(count=0; count<9; count++) sample_stop(count);
	
	    	sound_enabled = data;
	    }
	
	    if (sound_enabled != 0)
	    {
	        switch (offset)
	        {
			case 0:  if (data != 0) sample_start(0, 0, 0); break;  	/* Walk */
                        case 1:  if (data != 0) sample_start(0, 5, 0); break;  	/* Enemy Die 1 */
                        case 2:  if (data != 0)								  	/* Drop 1 */
					 {
						 if (sample_playing(1) == 0)
						 {
							 sample_stop(2);
							 sample_start(1, 3, 0);
						 }
					 }
					 else
					 	sample_stop(1);
					 	break;
	
			case 3:	 if ((data != 0) && (sample_playing(6) == 0))			/* Oxygen */
						sample_start(6, 9, 1);
	                 break;
	
	        case 4:  break;										/* Drop 2 */
	        case 5:  if (data != 0) sample_start(0, 5, 0); break;	/* Enemy Die 2 (use same sample as 1) */
	        case 6:  if ((data != 0) && (sample_playing(1)==0) && (sample_playing(3)==0))   /* Hang */
	                 	sample_start(2, 2, 0);
	                    break;
	
			case 7:  if (data != 0) 									/* Escape */
					 {
						 sample_stop(2);
						 sample_start(3, 4, 0);
					 }
					 else
					 	 sample_stop(3);
	                     break;
	
	    	case 8:  if (data != 0) sample_start(0, 1, 0); break;	/* Stairs */
	    	case 9:  if (data != 0)									/* Extend */
					 	sample_start(4, 8, 0);
					 else
						sample_stop(4);
		  			 break;
	
	        case 10: DAC_data_w(0, data); break;				/* Bonus */
			case 15: if (data != 0) sample_start(0, 6, 0); break;	/* Player Die */
			case 16: if (data != 0) sample_start(5, 7, 0); break;	/* Enemy Laugh */
	        case 17: if (data != 0) sample_start(0, 10, 0); break;	/* Coin - Not triggered by software */
	        }
	    }
	
	    //#ifdef MAME_DEBUG
            //	logerror("Sound output %x=%x\n",offset,data);
            //	#endif
	} };
	
	public static WriteHandlerPtr panic_sound_output2_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		panic_sound_output_w.handler(offset+15, data);
	} };
	
	public static WriteHandlerPtr cosmicg_output_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		int march_select=0;
                int gun_die_select=0;
                int sound_enabled=0;
	
	    /* Sound Enable / Disable */
	
	    if (offset == 12)
	    {
		    int count;
	
	    	sound_enabled = data;
	    	if (data == 0)
	        	for(count=0;count<9;count++) sample_stop(count);
	    }
	
	    if (sound_enabled != 0)
	    {
	        switch (offset)
	        {
			/* The schematics show a direct link to the sound amp  */
			/* as other cosmic series games, but it never seems to */
			/* be used for anything. It is implemented for sake of */
			/* completness. Maybe it plays a tune if you win ?     */
			case 1:  DAC_data_w(0, -data); break;
			case 2:  if (data != 0) sample_start (0, march_select, 0); break;	/* March Sound */
			case 3:  march_select = (march_select & 0xfe) | data; break;
	        case 4:  march_select = (march_select & 0xfd) | (data << 1); break;
	        case 5:  march_select = (march_select & 0xfb) | (data << 2); break;
	
	        case 6:  if (data != 0)  							/* Killer Attack (crawly thing at bottom of screen) */
						sample_start(1, 8, 1);
					 else
						sample_stop(1);
					 break;
	
		case 7:  if (data != 0)								/* Bonus Chance & Got Bonus */
					 {
						 sample_stop(4);
						 sample_start(4, 10, 0);
					 }
					 break;
	
		case 8:  if (data != 0)
					 {
						 if (sample_playing(4) == 0) sample_start(4, 9, 1);
					 }
					 else
					 	sample_stop(4);
					 break;
	
			case 9:  if (data !=0 ) sample_start(3, 11, 0); break;	/* Got Ship */
	//		case 11: watchdog_reset_w(0, 0); break;				/* Watchdog */
			case 13: if (data !=0 ) sample_start(8, 13-gun_die_select, 0); break;  /* Got Monster / Gunshot */
			case 14: gun_die_select = data; break;
			case 15: if (data !=0 ) sample_start(5, 14, 0); break;	/* Coin Extend (extra base) */
	        }
	    }
	
		//#ifdef MAME_DEBUG
	 	//if (offset != 11) logerror("Output %x=%x\n",offset,data);
                //#endif
	} };
	
	public static InterruptPtr panic_interrupt = new InterruptPtr() { public int handler() 
	{
		if (cpu_getiloops() != 0)
		{
	    	/* Coin insert - Trigger Sample */
	
	        /* mostly not noticed since sound is */
			/* only enabled if game in progress! */
	
	    	if ((input_port_3_r.handler(0) & 0xc0) != 0xc0)
	        	panic_sound_output_w.handler(17,1);
	
			return 0x00cf;		/* RST 08h */
	    }
	    else
	    {
	        return 0x00d7;		/* RST 10h */
	    }
	} };
	
	public static InterruptPtr cosmica_interrupt = new InterruptPtr() { public int handler() 
	{
	    pixel_clock = (pixel_clock + 2) & 63;
	
	    if (pixel_clock == 0)
	    {
			if ((readinputport(3) & 1) != 0)	/* Left Coin */
				return nmi_interrupt.handler();
	        else
	        	return ignore_interrupt.handler();
	    }
		else
	       	return ignore_interrupt.handler();
	} };
	
	public static InterruptPtr cosmicg_interrupt = new InterruptPtr() { public int handler() 
	{
		/* Increment Pixel Clock */
	
	    pixel_clock = (pixel_clock + 1) & 15;
	
	    /* Insert Coin */
	
		/* R Nabet : fixed to make this piece of code sensible.
		I assumed that the interrupt request lasted for as long as the coin was "sensed".
		It makes sense and works fine, but I cannot be 100% sure this is correct,
		as I have no Cosmic Guerilla console :-) . */
	
		if (pixel_clock == 0)
		{
			if ((readinputport(2) & 1) != 0) /* Coin */
			{
                            if (COSMICG_USES_TMS9980 != 0)
				/* on tms9980, a 6 on the interrupt bus means level 4 interrupt */
				cpu_irq_line_vector_w(0, 0, 6);
                            else
				/* tms9900 is more straightforward */
				cpu_irq_line_vector_w(0, 0, 4);
	
				cpu_set_irq_line(0, 0, ASSERT_LINE);
			}
			else
			{
				cpu_set_irq_line(0, 0, CLEAR_LINE);
			}
		}
	
		return ignore_interrupt.handler();
	} };
	
	public static InterruptPtr magspot2_interrupt = new InterruptPtr() { public int handler() 
	{
		/* Coin 1 causes an IRQ, Coin 2 an NMI */
		if ((input_port_4_r.handler(0) & 0x01) != 0)
		{
	  		return interrupt.handler();
		}
	
		if ((input_port_4_r.handler(0) & 0x02) != 0)
		{
			return nmi_interrupt.handler();
		}
	
		return ignore_interrupt.handler();
	} };
	
	
	public static ReadHandlerPtr cosmica_pixel_clock_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return pixel_clock;
	} };
	
	public static ReadHandlerPtr cosmicg_pixel_clock_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		/* The top four address lines from the CRTC are bits 0-3 */
	
		return (input_port_0_r.handler(0) & 0xf0) | pixel_clock;
	} };
	
	public static ReadHandlerPtr magspot2_coinage_dip_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return (input_port_5_r.handler(0) & (1 << (7 - offset)))!=0 ? 0 : 1;
	} };
	
	
	/* Has 8 way joystick, remap combinations to missing directions */
	
	public static ReadHandlerPtr nomnlnd_port_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		int control;
	    int fire = input_port_3_r.handler(0);
	
		if (offset != 0)
			control = input_port_1_r.handler(0);
	    else
			control = input_port_0_r.handler(0);
	
	    /* If firing - stop tank */
	
	    if ((fire & 0xc0) == 0) return 0xff;
	
	    /* set bit according to 8 way direction */
	
	    if ((control & 0x82) == 0 ) return 0xfe;    /* Up & Left */
	    if ((control & 0x0a) == 0 ) return 0xfb;    /* Down & Left */
	    if ((control & 0x28) == 0 ) return 0xef;    /* Down & Right */
	    if ((control & 0xa0) == 0 ) return 0xbf;    /* Up & Right */
	
	    return control;
	} };
	
	
	//#if COSMICG_USES_TMS9980
	
	/* 8-bit handler */
	//#define cosmicg_videoram_w cosmica_videoram_w
	//#define cosmicg_videoram_r MRA_RAM
	//#define COSMICG_ROM_LOAD ROM_LOAD
	
	//#else
	
	public static WriteHandlerPtr cosmicg_videoram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
	  /* 16-bit handler */
	  if ( (data & 0xff000000) == 0)
	    cosmica_videoram_w.handler(offset, (data >> 8) & 0xff);
	  if ( (data & 0x00ff0000) == 0)
	    cosmica_videoram_w.handler(offset + 1, data & 0xff);
	} };
	
	public static ReadHandlerPtr cosmicg_videoram_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return (videoram.read(offset) << 8) | videoram.read(offset+1);
	} };
	
	
	//#define COSMICG_ROM_LOAD ROM_LOAD16_WORD
	
	//#endif
	
	public static WriteHandlerPtr flip_screen_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		flip_screen_set(data&0x80);
	} };
	
	
	public static Memory_ReadAddress panic_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x3fff, MRA_ROM ),
		new Memory_ReadAddress( 0x4000, 0x5fff, MRA_RAM ),
		new Memory_ReadAddress( 0x6800, 0x6800, input_port_0_r ), /* IN1 */
		new Memory_ReadAddress( 0x6801, 0x6801, input_port_1_r ), /* IN2 */
		new Memory_ReadAddress( 0x6802, 0x6802, input_port_2_r ), /* DSW */
		new Memory_ReadAddress( 0x6803, 0x6803, input_port_3_r ), /* IN0 */
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress panic_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x3fff, MWA_ROM ),
		new Memory_WriteAddress( 0x4000, 0x5fff, cosmica_videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0x6000, 0x601f, MWA_RAM, spriteram, spriteram_size ),
		new Memory_WriteAddress( 0x7000, 0x700b, panic_sound_output_w ),
		new Memory_WriteAddress( 0x700c, 0x700e, panic_color_register_w ),
		new Memory_WriteAddress( 0x700f, 0x700f, flip_screen_w ),
		new Memory_WriteAddress( 0x7800, 0x7801, panic_sound_output2_w ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress cosmica_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x3fff, MRA_ROM ),
		new Memory_ReadAddress( 0x4000, 0x5fff, MRA_RAM ),
		new Memory_ReadAddress( 0x6800, 0x6800, input_port_0_r ), /* IN1 */
		new Memory_ReadAddress( 0x6801, 0x6801, input_port_1_r ), /* IN2 */
		new Memory_ReadAddress( 0x6802, 0x6802, input_port_2_r ), /* DSW */
		new Memory_ReadAddress( 0x6803, 0x6803, cosmica_pixel_clock_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress cosmica_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x3fff, MWA_ROM ),
		new Memory_WriteAddress( 0x4000, 0x5fff, cosmica_videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress( 0x6000, 0x601f, MWA_RAM ,spriteram, spriteram_size ),
		new Memory_WriteAddress( 0x7000, 0x700b, MWA_RAM ),   			/* Sound Triggers */
		new Memory_WriteAddress( 0x700c, 0x700e, panic_color_register_w ),
		new Memory_WriteAddress( 0x700f, 0x700f, flip_screen_w ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress cosmicg_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x1fff, MRA_ROM ),
		new Memory_ReadAddress( 0x2000, 0x3fff, cosmicg_videoram_r ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress cosmicg_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x1fff, MWA_ROM ),
		new Memory_WriteAddress( 0x2000, 0x3fff, cosmicg_videoram_w, videoram, videoram_size ),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static IO_ReadPort cosmicg_readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),	new IO_ReadPort( 0x00, 0x00, cosmicg_pixel_clock_r ),
		new IO_ReadPort( 0x01, 0x01, input_port_1_r ),
		new IO_ReadPort(MEMPORT_MARKER, 0)
	};
	public static IO_WritePort cosmicg_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),	new IO_WritePort( 0x00, 0x15, cosmicg_output_w ),
		new IO_WritePort( 0x16, 0x17, cosmicg_color_register_w ),
		new IO_WritePort(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress magspot2_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x2fff, MRA_ROM ),
		new Memory_ReadAddress( 0x3800, 0x3807, magspot2_coinage_dip_r ),
		new Memory_ReadAddress( 0x5000, 0x5000, input_port_0_r ),
		new Memory_ReadAddress( 0x5001, 0x5001, input_port_1_r ),
		new Memory_ReadAddress( 0x5002, 0x5002, input_port_2_r ),
		new Memory_ReadAddress( 0x5003, 0x5003, input_port_3_r ),
		new Memory_ReadAddress( 0x6000, 0x7fff, MRA_RAM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress magspot2_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x2fff, MWA_ROM ),
		new Memory_WriteAddress( 0x4000, 0x401f, MWA_RAM, spriteram, spriteram_size),
		new Memory_WriteAddress( 0x4800, 0x4800, DAC_0_data_w ),
		new Memory_WriteAddress( 0x480c, 0x480e, panic_color_register_w ),
		new Memory_WriteAddress( 0x480f, 0x480f, flip_screen_w ),
		new Memory_WriteAddress( 0x6000, 0x7fff, cosmica_videoram_w, videoram, videoram_size),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_ReadAddress nomnlnd_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_ReadAddress( 0x0000, 0x2fff, MRA_ROM ),
		new Memory_ReadAddress( 0x3800, 0x3807, magspot2_coinage_dip_r ),
		new Memory_ReadAddress( 0x5000, 0x5001, nomnlnd_port_r ),
		new Memory_ReadAddress( 0x5002, 0x5002, input_port_2_r ),
		new Memory_ReadAddress( 0x5003, 0x5003, input_port_3_r ),
		new Memory_ReadAddress( 0x6000, 0x7fff, MRA_RAM ),
		new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};
	public static Memory_WriteAddress nomnlnd_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),	new Memory_WriteAddress( 0x0000, 0x2fff, MWA_ROM ),
		new Memory_WriteAddress( 0x4000, 0x401f, MWA_RAM, spriteram, spriteram_size),
		new Memory_WriteAddress( 0x4807, 0x4807, nomnlnd_background_w ),
		new Memory_WriteAddress( 0x480a, 0x480a, DAC_0_data_w ),
		new Memory_WriteAddress( 0x480c, 0x480e, panic_color_register_w ),
		new Memory_WriteAddress( 0x480f, 0x480f, flip_screen_w ),
		new Memory_WriteAddress( 0x6000, 0x7fff, cosmica_videoram_w, videoram, videoram_size),
		new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	static InputPortPtr input_ports_panic = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON1 );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_4WAY );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_4WAY );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_UP    | IPF_4WAY );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_BUTTON2 );
		PORT_START();       /* IN2 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_4WAY | IPF_COCKTAIL );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_4WAY | IPF_COCKTAIL );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_4WAY | IPF_COCKTAIL );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_JOYSTICK_UP    | IPF_4WAY | IPF_COCKTAIL );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_BUTTON2 | IPF_COCKTAIL );
		PORT_START();       /* DSW */
		PORT_DIPNAME( 0x07, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_5C") );
	/* 0x06 and 0x07 disabled */
		PORT_DIPNAME( 0x08, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x00, "3000" );	PORT_DIPSETTING(    0x10, "5000" );	PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "3" );	PORT_DIPSETTING(    0x20, "4" );	PORT_DIPNAME( 0xc0, 0x40, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0x00, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "1C_3C") );
	
		PORT_START();       /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_START1 );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_START2 );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_COIN1 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_COIN2 );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_cosmica = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON1 );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_2WAY );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_START();       /* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY | IPF_COCKTAIL );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_2WAY | IPF_COCKTAIL );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_UNKNOWN );
		PORT_START();       /* IN2 */
		PORT_DIPNAME( 0x01, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x01, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x02, 0x02, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x02, "3" );	PORT_DIPSETTING(    0x00, "5" );	PORT_DIPNAME( 0x0c, 0x00, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0x08, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_2C") );
	/* 0c gives 1C_1C */
		PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x30, "5000" );	PORT_DIPSETTING(    0x20, "10000" );	PORT_DIPSETTING(    0x10, "15000" );	PORT_DIPSETTING(    0x00, "None" );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
		/* The coin slots are not memory mapped. Coin  causes a NMI, */
		/* This fake input port is used by the interrupt */
		/* handler to be notified of coin insertions. We use IMPULSE to */
		/* trigger exactly one interrupt, without having to check when the */
		/* user releases the key. */
	
		PORT_START(); 	/* FAKE */
		PORT_BIT_IMPULSE( 0x01, IP_ACTIVE_HIGH, IPT_COIN1, 1 );
	INPUT_PORTS_END(); }}; 
	
	/* These are used for the CR handling - This can be used to */
	/* from 1 to 16 bits from any bit offset between 0 and 4096 */
	
	/* Offsets are in BYTES, so bits 0-7 are at offset 0 etc.   */
	
	static InputPortPtr input_ports_cosmicg = new InputPortPtr(){ public void handler() { 
	PORT_START();  /* 4-7 */
		PORT_BIT( 0x10, IP_ACTIVE_LOW, IPT_START1 );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_START2 );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON1 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY );
		PORT_START();  /* 8-15 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_2WAY );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );	PORT_BIT( 0x04, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY | IPF_COCKTAIL);	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_2WAY | IPF_COCKTAIL);	PORT_DIPNAME( 0x30, 0x30, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x10, "1000" );	PORT_DIPSETTING(    0x20, "1500" );	PORT_DIPSETTING(    0x30, "2000" );	PORT_DIPSETTING(    0x00, "None" );	PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0x40, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPNAME( 0x80, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "3" );	PORT_DIPSETTING(    0x80, "5" );
		PORT_START();       /* Hard wired settings */
	
		/* The coin slots are not memory mapped. Coin causes INT 4  */
		/* This fake input port is used by the interrupt handler 	*/
		/* to be notified of coin insertions. */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_COIN1 );
		/* This dip switch is not read by the program at any time   */
		/* but is wired to enable or disable the flip screen output */
	
		PORT_DIPNAME( 0x02, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x02, DEF_STR( "Cocktail") );
	
		/* This odd setting is marked as shown on the schematic,    */
		/* and again, is not read by the program, but wired into    */
		/* the watchdog circuit. The book says to leave it off      */
	
		PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Unused") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
	
	INPUT_PORTS_END(); }}; 
	
	
	static InputPortPtr input_ports_magspot2 = new InputPortPtr(){ public void handler() { 
	PORT_START(); 	/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNUSED );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_2WAY );	PORT_BIT( 0x1c, IP_ACTIVE_LOW, IPT_UNUSED );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY );	PORT_DIPNAME( 0xc0, 0x40, "Bonus Game" );	PORT_DIPSETTING(    0x40, "5000" );	PORT_DIPSETTING(    0x80, "10000" );	PORT_DIPSETTING(    0xc0, "15000" );	PORT_DIPSETTING(    0x00, "None" );
		PORT_START(); 	/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNUSED );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_2WAY | IPF_COCKTAIL );	PORT_BIT( 0x1c, IP_ACTIVE_LOW, IPT_UNUSED );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY | IPF_COCKTAIL );	PORT_BIT( 0xc0, IP_ACTIVE_LOW, IPT_UNUSED );
		PORT_START(); 	/* IN2 */
		PORT_DIPNAME( 0x03, 0x01, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x01, "2000" );	PORT_DIPSETTING(    0x02, "3000" );	PORT_DIPSETTING(    0x03, "5000" );	PORT_DIPSETTING(    0x00, "None" );	PORT_DIPNAME( 0x04, 0x00, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x04, DEF_STR( "On") );
		PORT_DIPNAME( 0x18, 0x08, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "2" );	PORT_DIPSETTING(    0x08, "3" );	PORT_DIPSETTING(    0x10, "4" );	PORT_DIPSETTING(    0x18, "5" );	PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x20, DEF_STR( "Cocktail") );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
		PORT_START(); 	/* IN3 */
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_VBLANK );	PORT_BIT( 0x3e, IP_ACTIVE_LOW, IPT_UNUSED );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_BUTTON1 );
		/* Fake port to handle coins */
		PORT_START(); 	/* IN4 */
		PORT_BIT_IMPULSE( 0x01, IP_ACTIVE_HIGH, IPT_COIN1, 1 );	PORT_BIT_IMPULSE( 0x02, IP_ACTIVE_HIGH, IPT_COIN2, 1 );
		/* Fake port to handle coinage dip switches. Each bit goes to 3800-3807 */
		PORT_START(); 	/* IN5 */
		PORT_DIPNAME( 0x0f, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x0c, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x08, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x0d, DEF_STR( "4C_2C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x09, DEF_STR( "3C_2C") );
		PORT_DIPSETTING(    0x0e, DEF_STR( "4C_3C") );
		PORT_DIPSETTING(    0x0f, DEF_STR( "4C_4C") );
		PORT_DIPSETTING(    0x0a, DEF_STR( "3C_3C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "2C_2C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x0b, DEF_STR( "3C_4C") );
		PORT_DIPSETTING(    0x07, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_5C") );
		PORT_DIPNAME( 0xf0, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0xd0, DEF_STR( "4C_2C") );
		PORT_DIPSETTING(    0x50, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x90, DEF_STR( "3C_2C") );
		PORT_DIPSETTING(    0xe0, DEF_STR( "4C_3C") );
		PORT_DIPSETTING(    0xf0, DEF_STR( "4C_4C") );
		PORT_DIPSETTING(    0xa0, DEF_STR( "3C_3C") );
		PORT_DIPSETTING(    0x60, DEF_STR( "2C_2C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0xb0, DEF_STR( "3C_4C") );
		PORT_DIPSETTING(    0x70, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "1C_5C") );
	INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_devzone = new InputPortPtr(){ public void handler() { 
	PORT_START(); 	/* IN0 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNUSED );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_2WAY );	PORT_BIT( 0x1c, IP_ACTIVE_LOW, IPT_UNUSED );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY );	PORT_BIT( 0xc0, IP_ACTIVE_LOW, IPT_UNUSED );
		PORT_START(); 	/* IN1 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_UNUSED );	PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_2WAY | IPF_COCKTAIL );	PORT_BIT( 0x1c, IP_ACTIVE_LOW, IPT_UNUSED );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_2WAY | IPF_COCKTAIL );	PORT_BIT( 0xc0, IP_ACTIVE_LOW, IPT_UNUSED );
		PORT_START(); 	/* IN2 */
		PORT_DIPNAME( 0x03, 0x01, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x01, "4000" );	PORT_DIPSETTING(    0x02, "6000" );	PORT_DIPSETTING(    0x03, "8000" );	PORT_DIPSETTING(    0x00, "None" );	PORT_DIPNAME( 0x0c, 0x0c, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0x0c, "Use Coin A & B" );	PORT_DIPSETTING(    0x04, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x08, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x10, 0x10, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "2" );	PORT_DIPSETTING(    0x10, "3" );	PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x20, DEF_STR( "Cocktail") );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
		PORT_START(); 	/* IN3 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_VBLANK );	PORT_BIT( 0x3e, IP_ACTIVE_LOW, IPT_UNUSED );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_BUTTON1 );
		/* Fake port to handle coins */
		PORT_START(); 	/* IN4 */
		PORT_BIT_IMPULSE( 0x01, IP_ACTIVE_HIGH, IPT_COIN1, 1 );	PORT_BIT_IMPULSE( 0x02, IP_ACTIVE_HIGH, IPT_COIN2, 1 );
		PORT_START(); 	/* IN5 */
		PORT_DIPNAME( 0x0f, 0x00, DEF_STR( "Coin_A") );
		PORT_DIPSETTING(    0x0c, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x08, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0x0d, DEF_STR( "4C_2C") );
		PORT_DIPSETTING(    0x05, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x09, DEF_STR( "3C_2C") );
		PORT_DIPSETTING(    0x0e, DEF_STR( "4C_3C") );
		PORT_DIPSETTING(    0x0f, DEF_STR( "4C_4C") );
		PORT_DIPSETTING(    0x0a, DEF_STR( "3C_3C") );
		PORT_DIPSETTING(    0x06, DEF_STR( "2C_2C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x0b, DEF_STR( "3C_4C") );
		PORT_DIPSETTING(    0x07, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x01, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x02, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x03, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_5C") );
		PORT_DIPNAME( 0xf0, 0x00, DEF_STR( "Coin_B") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0xd0, DEF_STR( "4C_2C") );
		PORT_DIPSETTING(    0x50, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x90, DEF_STR( "3C_2C") );
		PORT_DIPSETTING(    0xe0, DEF_STR( "4C_3C") );
		PORT_DIPSETTING(    0xf0, DEF_STR( "4C_4C") );
		PORT_DIPSETTING(    0xa0, DEF_STR( "3C_3C") );
		PORT_DIPSETTING(    0x60, DEF_STR( "2C_2C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0xb0, DEF_STR( "3C_4C") );
		PORT_DIPSETTING(    0x70, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "1C_5C") );
	INPUT_PORTS_END(); }}; 
	
	
	static InputPortPtr input_ports_nomnlnd = new InputPortPtr(){ public void handler() { 
	PORT_START(); 	/* Controls - Remapped for game */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_UP    | IPF_8WAY );	PORT_BIT( 0x55, IP_ACTIVE_LOW, IPT_UNUSED );
		PORT_START(); 	/* IN1 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY | IPF_COCKTAIL );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY | IPF_COCKTAIL );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_COCKTAIL );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_UP    | IPF_8WAY | IPF_COCKTAIL );	PORT_BIT( 0x55, IP_ACTIVE_LOW, IPT_UNUSED );
		PORT_START(); 	/* IN2 */
		PORT_DIPNAME( 0x03, 0x02, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x01, "2000" );	PORT_DIPSETTING(    0x02, "3000" );	PORT_DIPSETTING(    0x03, "5000" );	PORT_DIPSETTING(    0x00, "None" );	PORT_DIPNAME( 0x0c, 0x00, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0x04, DEF_STR( "2C_1C") );
	//	PORT_DIPSETTING(    0x0c, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x08, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x10, 0x00, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "3" );	PORT_DIPSETTING(    0x10, "5" );	PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x20, DEF_STR( "Cocktail") );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
		PORT_START(); 	/* IN3 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_VBLANK );	PORT_BIT( 0x3e, IP_ACTIVE_LOW, IPT_UNUSED );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_BUTTON1 );
		/* Fake port to handle coin */
		PORT_START(); 	/* IN4 */
		PORT_BIT_IMPULSE( 0x02, IP_ACTIVE_HIGH, IPT_COIN1, 1 );
	/* Why is this here for this game? */
	//	PORT_START(); 	/* IN5 */
	/* 	PORT_DIPNAME( 0xf0, 0x00, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0xd0, DEF_STR( "4C_2C") );
		PORT_DIPSETTING(    0x50, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x90, DEF_STR( "3C_2C") );
		PORT_DIPSETTING(    0xe0, DEF_STR( "4C_3C") );
		PORT_DIPSETTING(    0xf0, DEF_STR( "4C_4C") );
		PORT_DIPSETTING(    0xa0, DEF_STR( "3C_3C") );
		PORT_DIPSETTING(    0x60, DEF_STR( "2C_2C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0xb0, DEF_STR( "3C_4C") );
		PORT_DIPSETTING(    0x70, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "1C_5C") ); */
	
	INPUT_PORTS_END(); }}; 
	
	
	static InputPortPtr input_ports_nomnlndg = new InputPortPtr(){ public void handler() { 
	PORT_START(); 	/* Controls - Remapped for game */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_UP    | IPF_8WAY );	PORT_BIT( 0x55, IP_ACTIVE_LOW, IPT_UNUSED );
		PORT_START(); 	/* IN1 */
		PORT_BIT( 0x02, IP_ACTIVE_LOW, IPT_JOYSTICK_LEFT  | IPF_8WAY | IPF_COCKTAIL );	PORT_BIT( 0x08, IP_ACTIVE_LOW, IPT_JOYSTICK_DOWN  | IPF_8WAY | IPF_COCKTAIL );	PORT_BIT( 0x20, IP_ACTIVE_LOW, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_COCKTAIL );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_JOYSTICK_UP    | IPF_8WAY | IPF_COCKTAIL );	PORT_BIT( 0x55, IP_ACTIVE_LOW, IPT_UNUSED );
		PORT_START(); 	/* IN2 */
		PORT_DIPNAME( 0x03, 0x02, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x01, "3000" );	PORT_DIPSETTING(    0x02, "5000" );	PORT_DIPSETTING(    0x03, "8000" );	PORT_DIPSETTING(    0x00, "None" );	PORT_DIPNAME( 0x0c, 0x00, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0x04, DEF_STR( "2C_1C") );
	//	PORT_DIPSETTING(    0x0c, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0x08, DEF_STR( "1C_2C") );
		PORT_DIPNAME( 0x10, 0x10, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x00, "2" );	PORT_DIPSETTING(    0x10, "3" );	PORT_DIPNAME( 0x20, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x20, DEF_STR( "Cocktail") );
		PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_START2 );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_START1 );
		PORT_START(); 	/* IN3 */
		PORT_BIT( 0x01, IP_ACTIVE_LOW, IPT_VBLANK );	PORT_BIT( 0x3e, IP_ACTIVE_LOW, IPT_UNUSED );	PORT_BIT( 0x40, IP_ACTIVE_LOW, IPT_BUTTON1 | IPF_COCKTAIL );	PORT_BIT( 0x80, IP_ACTIVE_LOW, IPT_BUTTON1 );
		/* Fake port to handle coin */
		PORT_START(); 	/* IN4 */
		PORT_BIT_IMPULSE( 0x02, IP_ACTIVE_HIGH, IPT_COIN1, 1 );
	/* Why is this here for this gane? */
	// 	PORT_START(); 	/* IN5 */
	/* 	PORT_DIPNAME( 0xf0, 0x00, DEF_STR( "Coinage") );
		PORT_DIPSETTING(    0xc0, DEF_STR( "4C_1C") );
		PORT_DIPSETTING(    0x80, DEF_STR( "3C_1C") );
		PORT_DIPSETTING(    0xd0, DEF_STR( "4C_2C") );
		PORT_DIPSETTING(    0x50, DEF_STR( "2C_1C") );
		PORT_DIPSETTING(    0x90, DEF_STR( "3C_2C") );
		PORT_DIPSETTING(    0xe0, DEF_STR( "4C_3C") );
		PORT_DIPSETTING(    0xf0, DEF_STR( "4C_4C") );
		PORT_DIPSETTING(    0xa0, DEF_STR( "3C_3C") );
		PORT_DIPSETTING(    0x60, DEF_STR( "2C_2C") );
		PORT_DIPSETTING(    0x00, DEF_STR( "1C_1C") );
		PORT_DIPSETTING(    0xb0, DEF_STR( "3C_4C") );
		PORT_DIPSETTING(    0x70, DEF_STR( "2C_3C") );
		PORT_DIPSETTING(    0x10, DEF_STR( "1C_2C") );
		PORT_DIPSETTING(    0x20, DEF_STR( "1C_3C") );
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_4C") );
		PORT_DIPSETTING(    0x40, DEF_STR( "1C_5C") ); */
	
	INPUT_PORTS_END(); }}; 
	
	
	static GfxLayout panic_spritelayout0 = new GfxLayout
	(
		16,16,	/* 16*16 sprites */
		48 ,	/* 64 sprites */
		2,	    /* 2 bits per pixel */
		new int[] { 4096*8, 0 },	/* the two bitplanes are separated */
		new int[] { 0,1,2,3,4,5,6,7,16*8+0,16*8+1,16*8+2,16*8+3,16*8+4,16*8+5,16*8+6,16*8+7 },
	   	new int[] { 15*8, 14*8, 13*8, 12*8, 11*8, 10*8, 9*8, 8*8, 7*8, 6*8, 5*8, 4*8, 3*8, 2*8, 1*8, 0*8 },
		32*8	/* every sprite takes 32 consecutive bytes */
	);
	
	static GfxLayout panic_spritelayout1 = new GfxLayout
	(
		16,16,	/* 16*16 sprites */
		16 ,	/* 16 sprites */
		2,	    /* 2 bits per pixel */
		new int[] { 4096*8, 0 },	/* the two bitplanes are separated */
		new int[] { 0,1,2,3,4,5,6,7,16*8+0,16*8+1,16*8+2,16*8+3,16*8+4,16*8+5,16*8+6,16*8+7 },
	  	new int[] { 15*8, 14*8, 13*8, 12*8, 11*8, 10*8, 9*8, 8*8, 7*8, 6*8, 5*8, 4*8, 3*8, 2*8, 1*8, 0*8 },
		32*8	/* every sprite takes 32 consecutive bytes */
	);
	
	static GfxLayout cosmica_spritelayout16 = new GfxLayout
	(
		16,16,				/* 16*16 sprites */
		64,					/* 64 sprites */
		2,					/* 2 bits per pixel */
		new int[] { 64*16*16, 0 },	/* the two bitplanes are separated */
		new int[] { 0,1,2,3,4,5,6,7,16*8+0,16*8+1,16*8+2,16*8+3,16*8+4,16*8+5,16*8+6,16*8+7},
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8, 8*8, 9*8, 10*8, 11*8, 12*8, 13*8, 14*8, 15*8 },
		32*8				/* every sprite takes 32 consecutive bytes */
	);
	
	static GfxLayout cosmica_spritelayout32 = new GfxLayout
	(
		32,32,				/* 32*32 sprites */
		16,					/* 16 sprites */
		2,					/* 2 bits per pixel */
		new int[] { 64*16*16, 0 },	/* the two bitplanes are separated */
		new int[] { 0,1,2,3,4,5,6,7,
		  32*8+0, 32*8+1, 32*8+2, 32*8+3, 32*8+4, 32*8+5, 32*8+6, 32*8+7,
		  64*8+0, 64*8+1, 64*8+2, 64*8+3, 64*8+4, 64*8+5, 64*8+6, 64*8+7,
	  	  96*8+0, 96*8+1, 96*8+2, 96*8+3, 96*8+4, 96*8+5, 96*8+6, 96*8+7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8, 8*8, 9*8, 10*8, 11*8, 12*8, 13*8, 14*8, 15*8, 16*8, 17*8,18*8,19*8,20*8,21*8,22*8,23*8,24*8,25*8,26*8,27*8,28*8,29*8,30*8,31*8 },
		128*8				/* every sprite takes 128 consecutive bytes */
	);
	
	static GfxLayout nomnlnd_treelayout = new GfxLayout
	(
		32,32,				/* 32*32 sprites */
		4,					/* 4 sprites */
		2,					/* 2 bits per pixel */
		new int[] { 0, 8*128*8 },	/* the two bitplanes are separated */
		new int[] { 0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31 },
		new int[] { 0*32, 1*32, 2*32, 3*32, 4*32, 5*32, 6*32, 7*32, 8*32, 9*32, 10*32, 11*32, 12*32, 13*32, 14*32, 15*32,
		 16*32, 17*32, 18*32, 19*32, 20*32, 21*32, 22*32, 23*32, 24*32, 25*32, 26*32, 27*32, 28*32, 29*32, 30*32, 31*32 },
		128*8				/* every sprite takes 128 consecutive bytes */
	);
	
	static GfxLayout nomnlnd_waterlayout = new GfxLayout
	(
		16,32,				/* 16*32 sprites */
		32,					/* 32 sprites */
		2,					/* 2 bits per pixel */
		new int[] { 0, 8*128*8 },	/* the two bitplanes are separated */
		new int[] { 0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15 },
		new int[] { 0*32, 1*32, 2*32, 3*32, 4*32, 5*32, 6*32, 7*32, 8*32, 9*32, 10*32, 11*32, 12*32, 13*32, 14*32, 15*32,
		 16*32, 17*32, 18*32, 19*32, 20*32, 21*32, 22*32, 23*32, 24*32, 25*32, 26*32, 27*32, 28*32, 29*32, 30*32, 31*32 },
		32 				/* To create a set of sprites 1 pixel displaced */
	);
	
	static GfxDecodeInfo panic_gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0x0a00, panic_spritelayout0, 0, 8 ),	/* Monsters              0a00-0fff */
		new GfxDecodeInfo( REGION_GFX1, 0x0200, panic_spritelayout0, 0, 8 ),	/* Monsters eating Man   0200-07ff */
		new GfxDecodeInfo( REGION_GFX1, 0x0800, panic_spritelayout1, 0, 8 ),	/* Man                   0800-09ff */
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	static GfxDecodeInfo cosmica_gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, cosmica_spritelayout16,  0, 16 ),
		new GfxDecodeInfo( REGION_GFX1, 0, cosmica_spritelayout32,  0, 16 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	static GfxDecodeInfo magspot2_gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, cosmica_spritelayout16,  0, 8 ),
		new GfxDecodeInfo( REGION_GFX1, 0, cosmica_spritelayout32,  0, 8 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	static GfxDecodeInfo nomnlnd_gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0x0000, cosmica_spritelayout16,  0,  8 ),
		new GfxDecodeInfo( REGION_GFX1, 0x0000, cosmica_spritelayout32,  0,  8 ),
		new GfxDecodeInfo( REGION_GFX2, 0x0000, nomnlnd_treelayout,      0,  9 ),
		new GfxDecodeInfo( REGION_GFX2, 0x0200, nomnlnd_waterlayout,     0,  10 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	
	static DACinterface dac_interface = new DACinterface
	(
		1,
		new int[] { 100 }
	);
	
	static String panic_sample_names[] =
	{
		"*panic",
		"walk.wav",
	    "upordown.wav",
	    "trapped.wav",
	    "falling.wav",
	    "escaping.wav",
		"ekilled.wav",
	    "death.wav",
	    "elaugh.wav",
	    "extral.wav",
	    "oxygen.wav",
	    "coin.wav",
		null       /* end of array */
	};
	
	static Samplesinterface panic_samples_interface = new Samplesinterface
	(
		9,	/* 9 channels */
		25,	/* volume */
		panic_sample_names
        );
	
	static String cosmicg_sample_names[] =
	{
		"*cosmicg",
		"cg_m0.wav",	/* 8 Different pitches of March Sound */
		"cg_m1.wav",
		"cg_m2.wav",
		"cg_m3.wav",
		"cg_m4.wav",
		"cg_m5.wav",
		"cg_m6.wav",
		"cg_m7.wav",
		"cg_att.wav",	/* Killer Attack */
		"cg_chnc.wav",	/* Bonus Chance  */
		"cg_gotb.wav",	/* Got Bonus - have not got correct sound for */
		"cg_dest.wav",	/* Gun Destroy */
		"cg_gun.wav",	/* Gun Shot */
		"cg_gotm.wav",	/* Got Monster */
		"cg_ext.wav",	/* Coin Extend */
		null       /* end of array */
	};
	
	static Samplesinterface cosmicg_samples_interface = new Samplesinterface
	(
		9,	/* 9 channels */
		25,	/* volume */
		cosmicg_sample_names
        );
	
	
	static MachineDriver machine_driver_panic = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				2000000,	/* 2 MHz? */
				panic_readmem,panic_writemem,null,null,
				panic_interrupt,2
			)
		},
		60, DEFAULT_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
	  	32*8, 32*8, new rectangle( 0*8, 32*8-1, 4*8, 28*8-1 ),
		panic_gfxdecodeinfo,
		16, 8*4,
		panic_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		generic_bitmapped_vh_start,
		generic_bitmapped_vh_stop,
		panic_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
	    new MachineSound[] {
			new MachineSound(
				SOUND_SAMPLES,
				panic_samples_interface
			),
			new MachineSound(
				SOUND_DAC,
				dac_interface
			)
	    }
	);
	
	static MachineDriver machine_driver_cosmica = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				1081600,
				cosmica_readmem,cosmica_writemem,null,null,
				cosmica_interrupt,32
			)
		},
		60, 2500,	/* frames per second, vblank duration */
		1,	/* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
	  	32*8, 32*8, new rectangle( 0*8, 32*8-1, 4*8, 28*8-1 ),
		cosmica_gfxdecodeinfo,
		8, 16*4,
		cosmica_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		generic_bitmapped_vh_start,
		generic_bitmapped_vh_stop,
		cosmica_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
                null
	);
	
	static MachineDriver machine_driver_cosmicg = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
	//#if COSMICG_USES_TMS9980
	//			CPU_TMS9980,
	//#else
				CPU_TMS9900,
	//#endif
				1228500,			/* 9.828 MHz Crystal */
				/* R Nabet : huh ? This would imply the crystal frequency is somehow divided by 2 before being
				fed to the tms9904 or tms9980.  Also, I have never heard of a tms9900/9980 operating under
				1.5MHz.  So, if someone can check this... */
				cosmicg_readmem,cosmicg_writemem,
				cosmicg_readport,cosmicg_writeport,
				cosmicg_interrupt,16
			)
		},
		60, 0,		/* frames per second, vblank duration */
		1,			/* single CPU, no need for interleaving */
		null,
	
		/* video hardware */
	  	32*8, 32*8, new rectangle( 0*8, 32*8-1, 4*8, 28*8-1 ),
		null, 			/* no gfxdecodeinfo - bitmapped display */
	    16,0,
	    cosmicg_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		generic_bitmapped_vh_start,
		generic_bitmapped_vh_stop,
		cosmicg_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_SAMPLES,
				cosmicg_samples_interface
			),
			new MachineSound(
				SOUND_DAC,
				dac_interface
			)
		}
	);
	
	static MachineDriver machine_driver_magspot2 = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				18432000/6,	/* 3.072 MHz ???? */
				magspot2_readmem,magspot2_writemem,null,null,
				magspot2_interrupt,1
			),
		},
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* 1 CPU slice per frame - interleaving is forced when a sound command is written */
		null,
	
		/* video hardware */
	  	32*8, 32*8, new rectangle( 0*8, 32*8-1, 4*8, 28*8-1 ),
		magspot2_gfxdecodeinfo,
		16, 8*4,
		magspot2_vh_convert_color_prom,
	
		VIDEO_TYPE_RASTER,
		null,
		generic_bitmapped_vh_start,
		generic_bitmapped_vh_stop,
		magspot2_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_DAC,
				dac_interface
			)
		}
	);
	
	static MachineDriver machine_driver_nomnlnd = new MachineDriver
	(
		/* basic machine hardware */
		new MachineCPU[] {
			new MachineCPU(
				CPU_Z80,
				18432000/6,	/* 3.072 MHz ???? */
				nomnlnd_readmem,nomnlnd_writemem,null,null,
				magspot2_interrupt,1
			)
		},
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */
		1,	/* 1 CPU slice per frame */
		null,
	
		/* video hardware */
	  	32*8, 32*8, new rectangle( 0*8, 32*8-1, 4*8, 28*8-1 ),
		nomnlnd_gfxdecodeinfo,
		16, 16*4,
		magspot2_vh_convert_color_prom,
		VIDEO_TYPE_RASTER,
		null,
		generic_bitmapped_vh_start,
		generic_bitmapped_vh_stop,
		nomnlnd_vh_screenrefresh,
	
		/* sound hardware */
		0,0,0,0,
		new MachineSound[] {
			new MachineSound(
				SOUND_DAC,
				dac_interface
			)
		}
	);
	
	
	public static InitDriverPtr init_cosmicg = new InitDriverPtr() { public void handler()
	{
		/* Roms have data pins connected different from normal */
	
		int count;
		int scrambled,normal;
	
	    for(count=0x1fff;count>=0;count--)
		{
	        scrambled = memory_region(REGION_CPU1).read(count);
	
	        normal = (scrambled >> 3 & 0x11)
	               | (scrambled >> 1 & 0x22)
	               | (scrambled << 1 & 0x44)
	               | (scrambled << 3 & 0x88);
	
	        memory_region(REGION_CPU1).write(count, normal);
	    }
	
	    /* Patch to avoid crash - Seems like duff romcheck routine */
	    /* I would expect it to be bitrot, but have two romsets    */
	    /* from different sources with the same problem!           */
	
	//#if COSMICG_USES_TMS9980
	    memory_region(REGION_CPU1).write(0x1e9e, 0x04);
	    memory_region(REGION_CPU1).write(0x1e9f, 0xc0);
	//#else
	//	((data16_t *)memory_region(REGION_CPU1))[0x1e9e/2] = 0x04c0;
	//#endif
	} };
	
	
	static RomLoadPtr rom_panic = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "spcpanic.1",   0x0000, 0x0800, 0x405ae6f9 );	ROM_LOAD( "spcpanic.2",   0x0800, 0x0800, 0xb6a286c5 );	ROM_LOAD( "spcpanic.3",   0x1000, 0x0800, 0x85ae8b2e );	ROM_LOAD( "spcpanic.4",   0x1800, 0x0800, 0xb6d4f52f );	ROM_LOAD( "spcpanic.5",   0x2000, 0x0800, 0x5b80f277 );	ROM_LOAD( "spcpanic.6",   0x2800, 0x0800, 0xb73babf0 );	ROM_LOAD( "spcpanic.7",   0x3000, 0x0800, 0xfc27f4e5 );
		ROM_REGION( 0x2000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "spcpanic.9",   0x0000, 0x0800, 0xeec78b4c );	ROM_LOAD( "spcpanic.10",  0x0800, 0x0800, 0xc9631c2d );	ROM_LOAD( "spcpanic.12",  0x1000, 0x0800, 0xe83423d0 );	ROM_LOAD( "spcpanic.11",  0x1800, 0x0800, 0xacea9df4 );
		ROM_REGION( 0x0020, REGION_PROMS, 0 );	ROM_LOAD( "82s123.sp",    0x0000, 0x0020, 0x35d43d2f );
		ROM_REGION( 0x0800, REGION_USER1, 0 );/* color map */
		ROM_LOAD( "spcpanic.8",   0x0000, 0x0800, 0x7da0b321 );ROM_END(); }}; 
	
	static RomLoadPtr rom_panica = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "panica.1",     0x0000, 0x0800, 0x289720ce );	ROM_LOAD( "spcpanic.2",   0x0800, 0x0800, 0xb6a286c5 );	ROM_LOAD( "spcpanic.3",   0x1000, 0x0800, 0x85ae8b2e );	ROM_LOAD( "spcpanic.4",   0x1800, 0x0800, 0xb6d4f52f );	ROM_LOAD( "spcpanic.5",   0x2000, 0x0800, 0x5b80f277 );	ROM_LOAD( "spcpanic.6",   0x2800, 0x0800, 0xb73babf0 );	ROM_LOAD( "panica.7",     0x3000, 0x0800, 0x3641cb7f );
		ROM_REGION( 0x2000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "spcpanic.9",   0x0000, 0x0800, 0xeec78b4c );	ROM_LOAD( "spcpanic.10",  0x0800, 0x0800, 0xc9631c2d );	ROM_LOAD( "spcpanic.12",  0x1000, 0x0800, 0xe83423d0 );	ROM_LOAD( "spcpanic.11",  0x1800, 0x0800, 0xacea9df4 );
		ROM_REGION( 0x0020, REGION_PROMS, 0 );	ROM_LOAD( "82s123.sp",    0x0000, 0x0020, 0x35d43d2f );
		ROM_REGION( 0x0800, REGION_USER1, 0 );/* color map */
		ROM_LOAD( "spcpanic.8",   0x0000, 0x0800, 0x7da0b321 );ROM_END(); }}; 
	
	static RomLoadPtr rom_panicger = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "spacepan.001", 0x0000, 0x0800, 0xa6d9515a );	ROM_LOAD( "spacepan.002", 0x0800, 0x0800, 0xcfc22663 );	ROM_LOAD( "spacepan.003", 0x1000, 0x0800, 0xe1f36893 );	ROM_LOAD( "spacepan.004", 0x1800, 0x0800, 0x01be297c );	ROM_LOAD( "spacepan.005", 0x2000, 0x0800, 0xe0d54805 );	ROM_LOAD( "spacepan.006", 0x2800, 0x0800, 0xaae1458e );	ROM_LOAD( "spacepan.007", 0x3000, 0x0800, 0x14e46e70 );
		ROM_REGION( 0x2000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "spcpanic.9",   0x0000, 0x0800, 0xeec78b4c );	ROM_LOAD( "spcpanic.10",  0x0800, 0x0800, 0xc9631c2d );	ROM_LOAD( "spcpanic.12",  0x1000, 0x0800, 0xe83423d0 );	ROM_LOAD( "spcpanic.11",  0x1800, 0x0800, 0xacea9df4 );
		ROM_REGION( 0x0020, REGION_PROMS, 0 );	ROM_LOAD( "82s123.sp",    0x0000, 0x0020, 0x35d43d2f );
		ROM_REGION( 0x0800, REGION_USER1, 0 );/* color map */
		ROM_LOAD( "spcpanic.8",   0x0000, 0x0800, 0x7da0b321 );ROM_END(); }}; 
	
	static RomLoadPtr rom_cosmica = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "ca.e3",        0x0000, 0x0800, 0x535ee0c5 );	ROM_LOAD( "ca.e4",        0x0800, 0x0800, 0xed3cf8f7 );	ROM_LOAD( "ca.e5",        0x1000, 0x0800, 0x6a111e5e );	ROM_LOAD( "ca.e6",        0x1800, 0x0800, 0xc9b5ca2a );	ROM_LOAD( "ca.e7",        0x2000, 0x0800, 0x43666d68 );
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );/* sprites */
		ROM_LOAD( "ca.n1",        0x0000, 0x0800, 0x431e866c );	ROM_LOAD( "ca.n2",        0x0800, 0x0800, 0xaa6c6079 );
		ROM_REGION( 0x0020, REGION_PROMS, 0 );	ROM_LOAD( "ca.d9",        0x0000, 0x0020, 0xdfb60f19 );
		ROM_REGION( 0x0400, REGION_USER1, 0 );/* color map */
		ROM_LOAD( "ca.e2",        0x0000, 0x0400, 0xea4ee931 );
		ROM_REGION( 0x0400, REGION_USER2, 0 );/* starfield generator */
		ROM_LOAD( "ca.sub",       0x0000, 0x0400, 0xacbd4e98 );ROM_END(); }}; 
	
	static RomLoadPtr rom_cosmica2 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "ca.e3",        0x0000, 0x0800, 0x535ee0c5 );	ROM_LOAD( "c3.bin",       0x0800, 0x0400, 0x699c849e );	ROM_LOAD( "d4.bin",       0x0c00, 0x0400, 0x168e38da );	ROM_LOAD( "ca.e5",        0x1000, 0x0800, 0x6a111e5e );	ROM_LOAD( "ca.e6",        0x1800, 0x0800, 0xc9b5ca2a );	ROM_LOAD( "i9.bin",       0x2000, 0x0400, 0x3bb57720 );	ROM_LOAD( "j0.bin",       0x2400, 0x0400, 0x4ff70f45 );
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );/* sprites */
		ROM_LOAD( "ca.n1",        0x0000, 0x0800, 0x431e866c );	ROM_LOAD( "ca.n2",        0x0800, 0x0800, 0xaa6c6079 );
		ROM_REGION( 0x0020, REGION_PROMS, 0 );	ROM_LOAD( "ca.d9",        0x0000, 0x0020, 0xdfb60f19 );
		ROM_REGION( 0x0400, REGION_USER1, 0 );/* color map */
		ROM_LOAD( "ca.e2",        0x0000, 0x0400, 0xea4ee931 );
		ROM_REGION( 0x0400, REGION_USER2, 0 );/* starfield generator */
		ROM_LOAD( "ca.sub",       0x0000, 0x0400, 0xacbd4e98 );ROM_END(); }}; 
	
	static RomLoadPtr rom_cosmicg = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 ); /* 8k for code */
		ROM_LOAD( "cosmicg1.bin",  0x0000, 0x0400, 0xe1b9f894 );
		ROM_LOAD( "cosmicg2.bin",  0x0400, 0x0400, 0x35c75346 );
		ROM_LOAD( "cosmicg3.bin",  0x0800, 0x0400, 0x82a49b48 );
		ROM_LOAD( "cosmicg4.bin",  0x0C00, 0x0400, 0x1c1c934c );
		ROM_LOAD( "cosmicg5.bin",  0x1000, 0x0400, 0xb1c00fbf );
		ROM_LOAD( "cosmicg6.bin",  0x1400, 0x0400, 0xf03454ce );
		ROM_LOAD( "cosmicg7.bin",  0x1800, 0x0400, 0xf33ebae7 );
		ROM_LOAD( "cosmicg8.bin",  0x1C00, 0x0400, 0x472e4990 );
	
		ROM_REGION( 0x0400, REGION_USER1, 0 );/* color map */
		ROM_LOAD( "cosmicg9.bin",  0x0000, 0x0400, 0x689c2c96 );ROM_END(); }}; 
	
	static RomLoadPtr rom_magspot2 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "ms.e3",   0x0000, 0x0800, 0xc0085ade );	ROM_LOAD( "ms.e4",   0x0800, 0x0800, 0xd534a68b );	ROM_LOAD( "ms.e5",   0x1000, 0x0800, 0x25513b2a );	ROM_LOAD( "ms.e7",   0x1800, 0x0800, 0x8836bbc4 );	ROM_LOAD( "ms.e6",   0x2000, 0x0800, 0x6a08ab94 );	ROM_LOAD( "ms.e8",   0x2800, 0x0800, 0x77c6d109 );
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );/* sprites */
		ROM_LOAD( "ms.n1",   0x0000, 0x0800, 0x1ab338d3 );	ROM_LOAD( "ms.n2",   0x0800, 0x0800, 0x9e1d63a2 );
		ROM_REGION( 0x0020, REGION_PROMS, 0 );	ROM_LOAD( "ms.d9",   0x0000, 0x0020, 0x36e2aa2a );
		ROM_REGION( 0x0400, REGION_USER1, 0 );/* color map */
		ROM_LOAD( "ms.e2",   0x0000, 0x0400, 0x89f23ebd );ROM_END(); }}; 
	
	static RomLoadPtr rom_devzone = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "dv1.e3",  0x0000, 0x0800, 0xc70faf00 );	ROM_LOAD( "dv2.e4",  0x0800, 0x0800, 0xeacfed61 );	ROM_LOAD( "dv3.e5",  0x1000, 0x0800, 0x7973317e );	ROM_LOAD( "dv5.e7",  0x1800, 0x0800, 0xb71a3989 );	ROM_LOAD( "dv4.e6",  0x2000, 0x0800, 0xa58c5b8c );	ROM_LOAD( "dv6.e8",  0x2800, 0x0800, 0x3930fb67 );
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );/* sprites */
		ROM_LOAD( "dv7.n1",  0x0000, 0x0800, 0xe7562fcf );	ROM_LOAD( "dv8.n2",  0x0800, 0x0800, 0xda1cbec1 );
		ROM_REGION( 0x0020, REGION_PROMS, 0 );	ROM_LOAD( "ms.d9",   0x0000, 0x0020, 0x36e2aa2a );
		ROM_REGION( 0x0400, REGION_USER1, 0 );/* color map */
		ROM_LOAD( "dz9.e2",  0x0000, 0x0400, 0x693855b6 );ROM_END(); }}; 
	
	static RomLoadPtr rom_nomnlnd = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "1.bin",  0x0000, 0x0800, 0xba117ba6 );	ROM_LOAD( "2.bin",  0x0800, 0x0800, 0xe5ed654f );	ROM_LOAD( "3.bin",  0x1000, 0x0800, 0x7fc42724 );	ROM_LOAD( "5.bin",  0x1800, 0x0800, 0x9cc2f1d9 );	ROM_LOAD( "4.bin",  0x2000, 0x0800, 0x0e8cd46a );	ROM_LOAD( "6.bin",  0x2800, 0x0800, 0xba472ba5 );
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );/* sprites */
		ROM_LOAD( "nml7.n1",  0x0000, 0x0800, 0xd08ed22f );	ROM_LOAD( "nml8.n2",  0x0800, 0x0800, 0x739009b4 );
		ROM_REGION( 0x0800, REGION_GFX2, ROMREGION_DISPOSE );/* tree + river */
		ROM_LOAD( "nl11.ic7", 0x0000, 0x0400, 0xe717b241 );	ROM_LOAD( "nl10.ic4", 0x0400, 0x0400, 0x5b13f64e );
		ROM_REGION( 0x0020, REGION_PROMS, 0 );	ROM_LOAD( "nml.clr",  0x0000, 0x0020, 0x65e911f9 );
		ROM_REGION( 0x0400, REGION_USER1, 0 );/* color map */
		ROM_LOAD( "nl9.e2",   0x0000, 0x0400, 0x9e05f14e );ROM_END(); }}; 
	
	static RomLoadPtr rom_nomnlndg = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "nml1.e3",  0x0000, 0x0800, 0xe212ed91 );	ROM_LOAD( "nml2.e4",  0x0800, 0x0800, 0xf66ef3d8 );	ROM_LOAD( "nml3.e5",  0x1000, 0x0800, 0xd422fc8a );	ROM_LOAD( "nml5.e7",  0x1800, 0x0800, 0xd58952ac );	ROM_LOAD( "nml4.e6",  0x2000, 0x0800, 0x994c9afb );	ROM_LOAD( "nml6.e8",  0x2800, 0x0800, 0x01ed2d8c );
		ROM_REGION( 0x1000, REGION_GFX1, ROMREGION_DISPOSE );/* sprites */
		ROM_LOAD( "nml7.n1",  0x0000, 0x0800, 0xd08ed22f );	ROM_LOAD( "nml8.n2",  0x0800, 0x0800, 0x739009b4 );
		ROM_REGION( 0x0800, REGION_GFX2, ROMREGION_DISPOSE );/* tree + river */
		ROM_LOAD( "nl11.ic7", 0x0000, 0x0400, 0xe717b241 );	ROM_LOAD( "nl10.ic4", 0x0400, 0x0400, 0x5b13f64e );
		ROM_REGION( 0x0020, REGION_PROMS, 0 );	ROM_LOAD( "nml.clr",  0x0000, 0x0020, 0x65e911f9 );
		ROM_REGION( 0x0400, REGION_USER1, 0 );/* color map */
		ROM_LOAD( "nl9.e2",   0x0000, 0x0400, 0x9e05f14e );ROM_END(); }}; 
	
	
	
	public static GameDriver driver_cosmicg	   = new GameDriver("1979"	,"cosmicg"	,"cosmic.java"	,rom_cosmicg,null	,machine_driver_cosmicg	,input_ports_cosmicg	,init_cosmicg	,ROT270	,	"Universal", "Cosmic Guerilla", GAME_NO_COCKTAIL );
	public static GameDriver driver_cosmica	   = new GameDriver("1979"	,"cosmica"	,"cosmic.java"	,rom_cosmica,null	,machine_driver_cosmica	,input_ports_cosmica	,null	,ROT270	,	"Universal", "Cosmic Alien", GAME_NO_SOUND );
	public static GameDriver driver_cosmica2	   = new GameDriver("1979"	,"cosmica2"	,"cosmic.java"	,rom_cosmica2,driver_cosmica	,machine_driver_cosmica	,input_ports_cosmica	,null	,ROT270	,	"Universal", "Cosmic Alien (older)", GAME_NO_SOUND );
	public static GameDriver driver_panic	   = new GameDriver("1980"	,"panic"	,"cosmic.java"	,rom_panic,null	,machine_driver_panic	,input_ports_panic	,null	,ROT270	,	"Universal", "Space Panic (set 1)" );
	public static GameDriver driver_panica	   = new GameDriver("1980"	,"panica"	,"cosmic.java"	,rom_panica,driver_panic	,machine_driver_panic	,input_ports_panic	,null	,ROT270	,	"Universal", "Space Panic (set 2)" );
	public static GameDriver driver_panicger	   = new GameDriver("1980"	,"panicger"	,"cosmic.java"	,rom_panicger,driver_panic	,machine_driver_panic	,input_ports_panic	,null	,ROT270	,	"Universal (ADP Automaten license)", "Space Panic (German)" );
	public static GameDriver driver_magspot2	   = new GameDriver("1980"	,"magspot2"	,"cosmic.java"	,rom_magspot2,null	,machine_driver_magspot2	,input_ports_magspot2	,null	,ROT270	,	"Universal", "Magical Spot II", GAME_IMPERFECT_SOUND );
	public static GameDriver driver_devzone	   = new GameDriver("1980"	,"devzone"	,"cosmic.java"	,rom_devzone,null	,machine_driver_magspot2	,input_ports_devzone	,null	,ROT270	,	"Universal", "Devil Zone", GAME_IMPERFECT_SOUND );
	public static GameDriver driver_nomnlnd	   = new GameDriver("1980?"	,"nomnlnd"	,"cosmic.java"	,rom_nomnlnd,null	,machine_driver_nomnlnd	,input_ports_nomnlnd	,null	,ROT270	,	"Universal", "No Man's Land", GAME_WRONG_COLORS | GAME_IMPERFECT_SOUND );
	public static GameDriver driver_nomnlndg	   = new GameDriver("1980?"	,"nomnlndg"	,"cosmic.java"	,rom_nomnlndg,driver_nomnlnd	,machine_driver_nomnlnd	,input_ports_nomnlndg	,null	,ROT270	,	"Universal (Gottlieb license)", "No Man's Land (Gottlieb)", GAME_WRONG_COLORS | GAME_IMPERFECT_SOUND );
}
