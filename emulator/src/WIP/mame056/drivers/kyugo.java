/***************************************************************************

This driver supports the following games:

Gyrodine - (c) 1984 Taito Corporation.
Son of Phoenix - (c) 1985 Associated Overseas MFR, Inc.
Repulse - (c) 1985 Sega
'99 The last war - (c) 1985 Proma
Flash Gal - (c) 1985 Sega
SRD Mission - (c) 1986 Taito Corporation.
Air Wolf - (c) 1987 Kyugo


Preliminary driver by:
Ernesto Corvi
someone@secureshell.com

Notes:
- attract mode in Son of Phoenix doesn't work

***************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP.mame056.drivers;

import static WIP.mame056.vidhrdw.kyugo.*;

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
import static WIP2.mame056.sound.ay8910.*;
import static WIP2.mame056.sound.ay8910H.*;
import static WIP2.mame056.vidhrdw.generic.*;

import static WIP2.arcadeflex056.fileio.*;
import static WIP2.mame056.palette.*;


public class kyugo
{
	
	static UBytePtr shared_ram = new UBytePtr();
	
	public static ReadHandlerPtr shared_ram_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		return shared_ram.read(offset);
	} };
	
	public static WriteHandlerPtr shared_ram_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		shared_ram.write(offset, data);
	} };
	
	public static ReadHandlerPtr special_spriteram_r  = new ReadHandlerPtr() { public int handler(int offset)
	{
		/* RAM is 4 bits wide, must set the high bits to 1 for the RAM test to pass */
		return spriteram_2.read(offset) | 0xf0;
	} };
	
	
	
	/* time to abuse the preprocessor for the memory/port maps */
	//Main_MemMap( gyrodine, 0xf000, 0xe000 )
        public static Memory_ReadAddress gyrodine_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),									
			new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),												
			new Memory_ReadAddress( 0x8000, 0x87ff, videoram_r ), /* background tiles */						
			new Memory_ReadAddress( 0x8800, 0x8fff, colorram_r ), /* background color */						
			new Memory_ReadAddress( 0x9000, 0x97ff, MRA_RAM ), /* foreground tiles */							
			new Memory_ReadAddress( 0x9800, 0x9fff, special_spriteram_r ),	/* 4 bits wide */				
			new Memory_ReadAddress( 0xa000, 0xa7ff, MRA_RAM ), /* sprites */									
			new Memory_ReadAddress( 0xf000, 0xf000+0x7ff, shared_ram_r ), /* 0xf000 RAM */					
			new Memory_ReadAddress( 0xf800, 0xffff, shared_ram_r ), /* 0xf000 mirror always here */			
			new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};																	
																						
		public static Memory_WriteAddress gyrodine_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),								
			new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),												
			new Memory_WriteAddress( 0x8000, 0x87ff, videoram_w, videoram, videoram_size ),					
			new Memory_WriteAddress( 0x8800, 0x8fff, colorram_w, colorram ),									
			new Memory_WriteAddress( 0x9000, 0x97ff, MWA_RAM, kyugo_videoram, kyugo_videoram_size ),			
			new Memory_WriteAddress( 0x9800, 0x9fff, MWA_RAM, spriteram_2 ),	/* 4 bits wide */				
			new Memory_WriteAddress( 0xa000, 0xa7ff, MWA_RAM, spriteram, spriteram_size ),					
			new Memory_WriteAddress( 0xa800, 0xa800, MWA_RAM, kyugo_back_scrollY_lo ), /* back scroll Y */	
			new Memory_WriteAddress( 0xb000, 0xb000, kyugo_gfxctrl_w ), /* back scroll MSB + other stuff */	
			new Memory_WriteAddress( 0xb800, 0xb800, MWA_RAM, kyugo_back_scrollX ), /* back scroll X */		
			new Memory_WriteAddress( 0xf000, 0xf000+0x7ff, shared_ram_w, shared_ram ), /* 0xf000 RAM */		
			new Memory_WriteAddress( 0xf800, 0xffff, shared_ram_w ), /* 0xf000 mirror always here */			
			new Memory_WriteAddress( 0xe000, 0xe000, watchdog_reset_w ),									
			new Memory_WriteAddress(MEMPORT_MARKER, 0)
        };
	//Main_MemMap( sonofphx, 0xf000, 0x0000 )
        public static Memory_ReadAddress sonofphx_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),									
			new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),												
			new Memory_ReadAddress( 0x8000, 0x87ff, videoram_r ), /* background tiles */						
			new Memory_ReadAddress( 0x8800, 0x8fff, colorram_r ), /* background color */						
			new Memory_ReadAddress( 0x9000, 0x97ff, MRA_RAM ), /* foreground tiles */							
			new Memory_ReadAddress( 0x9800, 0x9fff, special_spriteram_r ),	/* 4 bits wide */				
			new Memory_ReadAddress( 0xa000, 0xa7ff, MRA_RAM ), /* sprites */									
			new Memory_ReadAddress( 0xf000, 0xf000+0x7ff, shared_ram_r ), /* shared RAM */					
			new Memory_ReadAddress( 0xf800, 0xffff, shared_ram_r ), /* shared mirror always here */			
			new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};																	
																						
		public static Memory_WriteAddress sonofphx_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),								
			new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),												
			new Memory_WriteAddress( 0x8000, 0x87ff, videoram_w, videoram, videoram_size ),					
			new Memory_WriteAddress( 0x8800, 0x8fff, colorram_w, colorram ),									
			new Memory_WriteAddress( 0x9000, 0x97ff, MWA_RAM, kyugo_videoram, kyugo_videoram_size ),			
			new Memory_WriteAddress( 0x9800, 0x9fff, MWA_RAM, spriteram_2 ),	/* 4 bits wide */				
			new Memory_WriteAddress( 0xa000, 0xa7ff, MWA_RAM, spriteram, spriteram_size ),					
			new Memory_WriteAddress( 0xa800, 0xa800, MWA_RAM, kyugo_back_scrollY_lo ), /* back scroll Y */	
			new Memory_WriteAddress( 0xb000, 0xb000, kyugo_gfxctrl_w ), /* back scroll MSB + other stuff */	
			new Memory_WriteAddress( 0xb800, 0xb800, MWA_RAM, kyugo_back_scrollX ), /* back scroll X */		
			new Memory_WriteAddress( 0xf000, 0xf000+0x7ff, shared_ram_w, shared_ram ), /* shared RAM */		
			new Memory_WriteAddress( 0xf800, 0xffff, shared_ram_w ), /* shared mirror always here */			
			new Memory_WriteAddress( 0x0000, 0x0000, watchdog_reset_w ),									
			new Memory_WriteAddress(MEMPORT_MARKER, 0)
                };
	//Main_MemMap( flashgal, 0xf000, 0x0000 )
        public static Memory_ReadAddress flashgal_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),									
			new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),												
			new Memory_ReadAddress( 0x8000, 0x87ff, videoram_r ), /* background tiles */						
			new Memory_ReadAddress( 0x8800, 0x8fff, colorram_r ), /* background color */						
			new Memory_ReadAddress( 0x9000, 0x97ff, MRA_RAM ), /* foreground tiles */							
			new Memory_ReadAddress( 0x9800, 0x9fff, special_spriteram_r ),	/* 4 bits wide */				
			new Memory_ReadAddress( 0xa000, 0xa7ff, MRA_RAM ), /* sprites */									
			new Memory_ReadAddress( 0xf000, 0xf000+0x7ff, shared_ram_r ), /* shared RAM */					
			new Memory_ReadAddress( 0xf800, 0xffff, shared_ram_r ), /* shared mirror always here */			
			new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};																	
																						
		public static Memory_WriteAddress flashgal_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),								
			new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),												
			new Memory_WriteAddress( 0x8000, 0x87ff, videoram_w, videoram, videoram_size ),					
			new Memory_WriteAddress( 0x8800, 0x8fff, colorram_w, colorram ),									
			new Memory_WriteAddress( 0x9000, 0x97ff, MWA_RAM, kyugo_videoram, kyugo_videoram_size ),			
			new Memory_WriteAddress( 0x9800, 0x9fff, MWA_RAM, spriteram_2 ),	/* 4 bits wide */				
			new Memory_WriteAddress( 0xa000, 0xa7ff, MWA_RAM, spriteram, spriteram_size ),					
			new Memory_WriteAddress( 0xa800, 0xa800, MWA_RAM, kyugo_back_scrollY_lo ), /* back scroll Y */	
			new Memory_WriteAddress( 0xb000, 0xb000, kyugo_gfxctrl_w ), /* back scroll MSB + other stuff */	
			new Memory_WriteAddress( 0xb800, 0xb800, MWA_RAM, kyugo_back_scrollX ), /* back scroll X */		
			new Memory_WriteAddress( 0xf000, 0xf000+0x7ff, shared_ram_w, shared_ram ), /* shared RAM */		
			new Memory_WriteAddress( 0xf800, 0xffff, shared_ram_w ), /* shared mirror always here */			
			new Memory_WriteAddress( 0x0000, 0x0000, watchdog_reset_w ),									
			new Memory_WriteAddress(MEMPORT_MARKER, 0)
                };
	//Main_MemMap( srdmissn, 0xe000, 0x0000 )
        public static Memory_ReadAddress srdmissn_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),									
			new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),												
			new Memory_ReadAddress( 0x8000, 0x87ff, videoram_r ), /* background tiles */						
			new Memory_ReadAddress( 0x8800, 0x8fff, colorram_r ), /* background color */						
			new Memory_ReadAddress( 0x9000, 0x97ff, MRA_RAM ), /* foreground tiles */							
			new Memory_ReadAddress( 0x9800, 0x9fff, special_spriteram_r ),	/* 4 bits wide */				
			new Memory_ReadAddress( 0xa000, 0xa7ff, MRA_RAM ), /* sprites */									
			new Memory_ReadAddress( 0xe000, 0xe000+0x7ff, shared_ram_r ), /* shared RAM */					
			new Memory_ReadAddress( 0xf800, 0xffff, shared_ram_r ), /* shared mirror always here */			
			new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};																	
																						
		public static Memory_WriteAddress srdmissn_writemem[]={
		new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),								
			new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),												
			new Memory_WriteAddress( 0x8000, 0x87ff, videoram_w, videoram, videoram_size ),					
			new Memory_WriteAddress( 0x8800, 0x8fff, colorram_w, colorram ),									
			new Memory_WriteAddress( 0x9000, 0x97ff, MWA_RAM, kyugo_videoram, kyugo_videoram_size ),			
			new Memory_WriteAddress( 0x9800, 0x9fff, MWA_RAM, spriteram_2 ),	/* 4 bits wide */				
			new Memory_WriteAddress( 0xa000, 0xa7ff, MWA_RAM, spriteram, spriteram_size ),					
			new Memory_WriteAddress( 0xa800, 0xa800, MWA_RAM, kyugo_back_scrollY_lo ), /* back scroll Y */	
			new Memory_WriteAddress( 0xb000, 0xb000, kyugo_gfxctrl_w ), /* back scroll MSB + other stuff */	
			new Memory_WriteAddress( 0xb800, 0xb800, MWA_RAM, kyugo_back_scrollX ), /* back scroll X */		
			new Memory_WriteAddress( 0xe000, 0xe000+0x7ff, shared_ram_w, shared_ram ), /* shared RAM */		
			new Memory_WriteAddress( 0xf800, 0xffff, shared_ram_w ), /* shared mirror always here */			
			new Memory_WriteAddress( 0x0000, 0x0000, watchdog_reset_w ),									
			new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	/* actual definitions */	
	//Sub_MemMap( gyrodine, 0x1fff, 0x4000, 0x0000, 0x8080, 0x8040, 0x8000 )
        public static Memory_ReadAddress gyrodine_sub_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),					
			new Memory_ReadAddress( 0x0000, 0x1fff, MRA_ROM ),									
			new Memory_ReadAddress( 0x4000, 0x4000+0x7ff, shared_ram_r ), /* shared RAM */		
			new Memory_ReadAddress( 0x0000, 0x0000+0x7ff, MRA_RAM ), /* extra RAM */		
			new Memory_ReadAddress( 0x8080, 0x8080, input_port_2_r ), /* input port */					
			new Memory_ReadAddress( 0x8040, 0x8040, input_port_3_r ), /* input port */					
			new Memory_ReadAddress( 0x8000, 0x8000, input_port_4_r ), /* input port */					
			new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};														
																			
        public static Memory_WriteAddress gyrodine_sub_writemem[]={
        new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),				
                new Memory_WriteAddress( 0x0000, 0x1fff, MWA_ROM ),									
                new Memory_WriteAddress( 0x4000, 0x4000+0x7ff, shared_ram_w ), /* shared RAM */		
                new Memory_WriteAddress( 0x0000, 0x0000+0x7ff, MWA_RAM ), /* extra RAM */		
                new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
        
	//Sub_MemMap( sonofphx, 0x7fff, 0xa000, 0x0000, 0xc080, 0xc040, 0xc000 )
        public static Memory_ReadAddress sonofphx_sub_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),					
			new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),									
			new Memory_ReadAddress( 0xa000, 0xa000+0x7ff, shared_ram_r ), /* shared RAM */		
			new Memory_ReadAddress( 0x0000, 0x0000+0x7ff, MRA_RAM ), /* extra RAM */		
			new Memory_ReadAddress( 0xc080, 0xc080, input_port_2_r ), /* input port */					
			new Memory_ReadAddress( 0xc040, 0xc040, input_port_3_r ), /* input port */					
			new Memory_ReadAddress( 0xc000, 0xc000, input_port_4_r ), /* input port */					
			new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};														
																			
        public static Memory_WriteAddress sonofphx_sub_writemem[]={
        new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),				
                new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),									
                new Memory_WriteAddress( 0xa000, 0xa000+0x7ff, shared_ram_w ), /* shared RAM */		
                new Memory_WriteAddress( 0x0000, 0x0000+0x7ff, MWA_RAM ), /* extra RAM */		
                new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
        //Sub_MemMap( flashgal, 0x7fff, 0xa000, 0x0000, 0xc080, 0xc040, 0xc000 )
        public static Memory_ReadAddress flashgal_sub_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),					
			new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),									
			new Memory_ReadAddress( 0xa000, 0xa000+0x7ff, shared_ram_r ), /* shared RAM */		
			new Memory_ReadAddress( 0x0000, 0x0000+0x7ff, MRA_RAM ), /* extra RAM */		
			new Memory_ReadAddress( 0xc080, 0xc080, input_port_2_r ), /* input port */					
			new Memory_ReadAddress( 0xc040, 0xc040, input_port_3_r ), /* input port */					
			new Memory_ReadAddress( 0xc000, 0xc000, input_port_4_r ), /* input port */					
			new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};														
																			
        public static Memory_WriteAddress flashgal_sub_writemem[]={
        new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),				
                new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),									
                new Memory_WriteAddress( 0xa000, 0xa000+0x7ff, shared_ram_w ), /* shared RAM */		
                new Memory_WriteAddress( 0x0000, 0x0000+0x7ff, MWA_RAM ), /* extra RAM */		
                new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
                
	//Sub_MemMap( srdmissn, 0x7fff, 0x8000, 0x8800, 0xf400, 0xf401, 0xf402 )
        //#define Sub_MemMap( name, rom_end, shared, extra_ram, in0, in1, in2 )	
	public static Memory_ReadAddress srdmissn_sub_readmem[]={
		new Memory_ReadAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),					
			new Memory_ReadAddress( 0x0000, 0x7fff, MRA_ROM ),									
			new Memory_ReadAddress( 0x8000, 0x8000+0x7ff, shared_ram_r ), /* shared RAM */		
			new Memory_ReadAddress( 0x8800, 0x8800+0x7ff, MRA_RAM ), /* extra RAM */		
			new Memory_ReadAddress( 0xf400, 0xf400, input_port_2_r ), /* input port */					
			new Memory_ReadAddress( 0xf401, 0xf401, input_port_3_r ), /* input port */					
			new Memory_ReadAddress( 0xf402, 0xf402, input_port_4_r ), /* input port */					
			new Memory_ReadAddress(MEMPORT_MARKER, 0)
	};														
																			
        public static Memory_WriteAddress srdmissn_sub_writemem[]={
        new Memory_WriteAddress(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_MEM | MEMPORT_WIDTH_8),				
                new Memory_WriteAddress( 0x0000, 0x7fff, MWA_ROM ),									
                new Memory_WriteAddress( 0x8000, 0x8000+0x7ff, shared_ram_w ), /* shared RAM */		
                new Memory_WriteAddress( 0x8800, 0x8800+0x7ff, MWA_RAM ), /* extra RAM */		
                new Memory_WriteAddress(MEMPORT_MARKER, 0)
	};
	
	public static WriteHandlerPtr sub_cpu_control_w = new WriteHandlerPtr() {public void handler(int offset, int data)
	{
		if ((data & 1)!=0)
			cpu_set_reset_line(1,CLEAR_LINE);
		else
			cpu_set_reset_line(1,ASSERT_LINE);
	} };
	
	//#define Main_PortMap( name, base )								
	//Main_PortMap( gyrodine, 0x00 )
        public static IO_WritePort gyrodine_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),				
			new IO_WritePort( 0x00+0, 0x00+0, interrupt_enable_w ),					
			new IO_WritePort( 0x00+1, 0x00+1, kyugo_flipscreen_w ),					
			new IO_WritePort( 0x00+2, 0x00+2, sub_cpu_control_w ),					
			new IO_WritePort(MEMPORT_MARKER, 0)
	};
	//Main_PortMap( sonofphx, 0x00 )
        public static IO_WritePort sonofphx_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),				
			new IO_WritePort( 0x00+0, 0x00+0, interrupt_enable_w ),					
			new IO_WritePort( 0x00+1, 0x00+1, kyugo_flipscreen_w ),					
			new IO_WritePort( 0x00+2, 0x00+2, sub_cpu_control_w ),					
			new IO_WritePort(MEMPORT_MARKER, 0)
	};
	//Main_PortMap( flashgal, 0x40 )
        public static IO_WritePort flashgal_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),				
			new IO_WritePort( 0x40+0, 0x40+0, interrupt_enable_w ),					
			new IO_WritePort( 0x40+1, 0x40+1, kyugo_flipscreen_w ),					
			new IO_WritePort( 0x40+2, 0x40+2, sub_cpu_control_w ),					
			new IO_WritePort(MEMPORT_MARKER, 0)
	};
	//Main_PortMap( srdmissn, 0x08 )
        public static IO_WritePort srdmissn_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),				
			new IO_WritePort( 0x08+0, 0x08+0, interrupt_enable_w ),					
			new IO_WritePort( 0x08+1, 0x08+1, kyugo_flipscreen_w ),					
			new IO_WritePort( 0x08+2, 0x08+2, sub_cpu_control_w ),					
			new IO_WritePort(MEMPORT_MARKER, 0)
	};
	//#define Sub_PortMap( name, ay0_base, ay1_base )					
	//Sub_PortMap( gyrodine, 0x00, 0xc0 )
        public static IO_ReadPort gyrodine_sub_readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),			
			new IO_ReadPort( 0x00+2, 0x00+2, AY8910_read_port_0_r ),		
			new IO_ReadPort(MEMPORT_MARKER, 0)
	};												
																	
		public static IO_WritePort gyrodine_sub_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),			
			new IO_WritePort( 0x00+0, 0x00+0, AY8910_control_port_0_w ),	
			new IO_WritePort( 0x00+1, 0x00+1, AY8910_write_port_0_w ),		
			new IO_WritePort( 0xc0+0, 0xc0+0, AY8910_control_port_1_w ),	
			new IO_WritePort( 0xc0+1, 0xc0+1, AY8910_write_port_1_w ),		
			new IO_WritePort(MEMPORT_MARKER, 0)
	};        
	//Sub_PortMap( sonofphx, 0x00, 0x40 )
        public static IO_ReadPort sonofphx_sub_readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),			
			new IO_ReadPort( 0x00+2, 0x00+2, AY8910_read_port_0_r ),		
			new IO_ReadPort(MEMPORT_MARKER, 0)
	};												
																	
		public static IO_WritePort sonofphx_sub_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),			
			new IO_WritePort( 0x00+0, 0x00+0, AY8910_control_port_0_w ),	
			new IO_WritePort( 0x00+1, 0x00+1, AY8910_write_port_0_w ),		
			new IO_WritePort( 0x40+0, 0x40+0, AY8910_control_port_1_w ),	
			new IO_WritePort( 0x40+1, 0x40+1, AY8910_write_port_1_w ),		
			new IO_WritePort(MEMPORT_MARKER, 0)
	};
	//Sub_PortMap( flashgal, 0x00, 0x40 )
        public static IO_ReadPort flashgal_sub_readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),			
			new IO_ReadPort( 0x00+2, 0x00+2, AY8910_read_port_0_r ),		
			new IO_ReadPort(MEMPORT_MARKER, 0)
	};												
																	
		public static IO_WritePort flashgal_sub_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),			
			new IO_WritePort( 0x00+0, 0x00+0, AY8910_control_port_0_w ),	
			new IO_WritePort( 0x00+1, 0x00+1, AY8910_write_port_0_w ),		
			new IO_WritePort( 0x40+0, 0x40+0, AY8910_control_port_1_w ),	
			new IO_WritePort( 0x40+1, 0x40+1, AY8910_write_port_1_w ),		
			new IO_WritePort(MEMPORT_MARKER, 0)
	};
	//Sub_PortMap( srdmissn, 0x80, 0x84 )
        public static IO_ReadPort srdmissn_sub_readport[]={
		new IO_ReadPort(MEMPORT_MARKER, MEMPORT_DIRECTION_READ | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),			
			new IO_ReadPort( 0x80+2, 0x80+2, AY8910_read_port_0_r ),		
			new IO_ReadPort(MEMPORT_MARKER, 0)
	};												
																	
		public static IO_WritePort srdmissn_sub_writeport[]={
		new IO_WritePort(MEMPORT_MARKER, MEMPORT_DIRECTION_WRITE | MEMPORT_TYPE_IO | MEMPORT_WIDTH_8),			
			new IO_WritePort( 0x80+0, 0x80+0, AY8910_control_port_0_w ),	
			new IO_WritePort( 0x80+1, 0x80+1, AY8910_write_port_0_w ),		
			new IO_WritePort( 0x84+0, 0x84+0, AY8910_control_port_1_w ),	
			new IO_WritePort( 0x84+1, 0x84+1, AY8910_write_port_1_w ),		
			new IO_WritePort(MEMPORT_MARKER, 0)
	};
	
	/***************************************************************************
	
		Input Ports
	
	***************************************************************************/
	
	public static void START_COINS(){
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_COIN1 );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_COIN2 );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_SERVICE1 );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_START1 );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_START2 );
        }
	
        public static void JOYSTICK_1(){
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT  | IPF_8WAY );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_8WAY );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP    | IPF_8WAY );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN  | IPF_8WAY );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_BUTTON1 );
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_BUTTON2 );
        }
        
	public static void JOYSTICK_2(){
		PORT_BIT( 0x01, IP_ACTIVE_HIGH, IPT_JOYSTICK_LEFT  | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x02, IP_ACTIVE_HIGH, IPT_JOYSTICK_RIGHT | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x04, IP_ACTIVE_HIGH, IPT_JOYSTICK_UP    | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x08, IP_ACTIVE_HIGH, IPT_JOYSTICK_DOWN  | IPF_8WAY | IPF_COCKTAIL );
		PORT_BIT( 0x10, IP_ACTIVE_HIGH, IPT_BUTTON1 | IPF_COCKTAIL );
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_BUTTON2 | IPF_COCKTAIL );
        }
        
	public static void COIN_A_B(){
		PORT_DIPNAME( 0x07, 0x07, DEF_STR( "Coin_A") ); 
		PORT_DIPSETTING(    0x02, DEF_STR( "2C_1C") ); 
		PORT_DIPSETTING(    0x01, DEF_STR( "3C_2C") ); 
		PORT_DIPSETTING(    0x07, DEF_STR( "1C_1C") ); 
		PORT_DIPSETTING(    0x06, DEF_STR( "1C_2C") ); 
		PORT_DIPSETTING(    0x05, DEF_STR( "1C_3C") ); 
		PORT_DIPSETTING(    0x04, DEF_STR( "1C_4C") ); 
		PORT_DIPSETTING(    0x03, DEF_STR( "1C_6C") ); 
		PORT_DIPSETTING(    0x00, DEF_STR( "Free_Play") ); 
		PORT_DIPNAME( 0x38, 0x38, DEF_STR( "Coin_B") ); 
		PORT_DIPSETTING(    0x00, DEF_STR( "5C_1C") ); 
		PORT_DIPSETTING(    0x08, DEF_STR( "4C_1C") ); 
		PORT_DIPSETTING(    0x10, DEF_STR( "3C_1C") ); 
		PORT_DIPSETTING(    0x18, DEF_STR( "2C_1C") ); 
		PORT_DIPSETTING(    0x38, DEF_STR( "1C_1C") ); 
		PORT_DIPSETTING(    0x20, DEF_STR( "3C_4C") ); 
		PORT_DIPSETTING(    0x30, DEF_STR( "1C_2C") ); 
		PORT_DIPSETTING(    0x28, DEF_STR( "1C_3C") );
        }
	
	static InputPortPtr input_ports_gyrodine = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x03, "3" );	PORT_DIPSETTING(    0x02, "4" );	PORT_DIPSETTING(    0x01, "5" );	PORT_DIPSETTING(    0x00, "6" );	PORT_DIPNAME( 0x04, 0x04, DEF_STR( "Unused") );
		PORT_DIPSETTING(    0x04, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x08, 0x08, DEF_STR( "Unused") );
		PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x10, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0x10, "Easy" );	PORT_DIPSETTING(    0x00, "Hard" );	PORT_DIPNAME( 0x20, 0x20, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x20, "20000 50000" );	PORT_DIPSETTING(    0x00, "40000 70000" );	PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x80, 0x80, "Freeze" );	PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START();       /* DSW2 */
                COIN_A_B();
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unused") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unused") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START(); 	/* IN0 */
	    START_COINS();
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN1 */
	    JOYSTICK_1();
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN2 */
	    JOYSTICK_2();
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_sonofphx = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x03, "3" );	PORT_DIPSETTING(    0x02, "4" );	PORT_DIPSETTING(    0x01, "5" );	PORT_DIPSETTING(    0x00, "6" );	PORT_DIPNAME( 0x04, 0x04, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x04, "Every 50000" );	PORT_DIPSETTING(    0x00, "Every 70000" );	PORT_DIPNAME( 0x08, 0x08, "Slow Motion" );	PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_BITX(0x10,     0x10, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Invulnerability", IP_KEY_NONE, IP_JOY_NONE );	PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x20, "Sound Test" );	PORT_DIPSETTING(    0x20, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x80, 0x80, "Freeze" );	PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START();       /* DSW2 */
	    COIN_A_B();
		PORT_DIPNAME( 0xc0, 0x80, DEF_STR( "Difficulty") );
		PORT_DIPSETTING(    0xc0, "Easy" );	PORT_DIPSETTING(    0x80, "Normal" );	PORT_DIPSETTING(    0x40, "Hard" );	PORT_DIPSETTING(    0x00, "Hardest" );
		PORT_START(); 	/* IN0 */
	    START_COINS();
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN1 */
	    JOYSTICK_1();
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN2 */
	    JOYSTICK_2();
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_airwolf = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x03, "4" );	PORT_DIPSETTING(    0x02, "5" );	PORT_DIPSETTING(    0x01, "6" );	PORT_DIPSETTING(    0x00, "7" );	PORT_DIPNAME( 0x04, 0x00, "Allow Continue" );	PORT_DIPSETTING(    0x04, DEF_STR( "No") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Yes") );
		PORT_DIPNAME( 0x08, 0x08, "Slow Motion" );	PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_BITX(0x10,     0x10, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Invulnerability", IP_KEY_NONE, IP_JOY_NONE );	PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x20, "Sound Test" );	PORT_DIPSETTING(    0x20, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x80, 0x80, "Freeze" );	PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START();       /* DSW2 */
	    COIN_A_B();
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START(); 	/* IN0 */
	    START_COINS();
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN1 */
	    JOYSTICK_1();
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN2 */
	    JOYSTICK_2();
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_flashgal = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x03, "3" );	PORT_DIPSETTING(    0x02, "4" );	PORT_DIPSETTING(    0x01, "5" );	PORT_DIPSETTING(    0x00, "6" );	PORT_DIPNAME( 0x04, 0x04, DEF_STR( "Bonus_Life") );
		PORT_DIPSETTING(    0x04, "Every 50000" );	PORT_DIPSETTING(    0x00, "Every 70000" );	PORT_DIPNAME( 0x08, 0x08, "Slow Motion" );	PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x10, 0x00, "Allow Continue" );	PORT_DIPSETTING(    0x10, DEF_STR( "No") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Yes") );
		PORT_DIPNAME( 0x20, 0x20, "Sound Test" );	PORT_DIPSETTING(    0x20, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x80, 0x80, "Freeze" );	PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START();       /* DSW2 */
	    COIN_A_B();
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START(); 	/* IN0 */
	    START_COINS();
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN1 */
	    JOYSTICK_1();
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN2 */
	    JOYSTICK_2();
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_skywolf = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x03, "3" );	PORT_DIPSETTING(    0x02, "4" );	PORT_DIPSETTING(    0x01, "5" );	PORT_DIPSETTING(    0x00, "6" );	PORT_DIPNAME( 0x04, 0x00, "Allow Continue" );	PORT_DIPSETTING(    0x04, DEF_STR( "No") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Yes") );
		PORT_DIPNAME( 0x08, 0x08, "Slow Motion" );	PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_BITX(0x10,     0x10, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Invulnerability", IP_KEY_NONE, IP_JOY_NONE );	PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x20, "Sound Test" );	PORT_DIPSETTING(    0x20, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x80, 0x80, "Freeze" );	PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START();       /* DSW2 */
	    COIN_A_B();
		PORT_DIPNAME( 0x40, 0x40, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x80, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START(); 	/* IN0 */
	    START_COINS();
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN1 */
	    JOYSTICK_1();
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN2 */
	    JOYSTICK_2();
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );INPUT_PORTS_END(); }}; 
	
	static InputPortPtr input_ports_srdmissn = new InputPortPtr(){ public void handler() { 
	PORT_START();       /* DSW1 */
		PORT_DIPNAME( 0x03, 0x03, DEF_STR( "Lives") );
		PORT_DIPSETTING(    0x03, "3" );	PORT_DIPSETTING(    0x02, "4" );	PORT_DIPSETTING(    0x01, "5" );	PORT_DIPSETTING(    0x00, "6" );	PORT_DIPNAME( 0x04, 0x04, "Bonus Life/Continue" );	PORT_DIPSETTING(    0x04, "Every 50000/No" );	PORT_DIPSETTING(    0x00, "Every 70000/Yes" );	PORT_DIPNAME( 0x08, 0x08, "Slow Motion" );	PORT_DIPSETTING(    0x08, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_BITX(0x10,     0x10, IPT_DIPSWITCH_NAME | IPF_CHEAT, "Invulnerability", IP_KEY_NONE, IP_JOY_NONE );	PORT_DIPSETTING(    0x10, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x20, 0x20, "Sound Test" );	PORT_DIPSETTING(    0x20, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
		PORT_DIPNAME( 0x40, 0x00, DEF_STR( "Cabinet") );
		PORT_DIPSETTING(    0x00, DEF_STR( "Upright") );
		PORT_DIPSETTING(    0x40, DEF_STR( "Cocktail") );
		PORT_DIPNAME( 0x80, 0x80, "Freeze" );	PORT_DIPSETTING(    0x80, DEF_STR( "Off") );
		PORT_DIPSETTING(    0x00, DEF_STR( "On") );
	
		PORT_START();       /* DSW2 */
	    COIN_A_B();
		PORT_DIPNAME( 0xc0, 0x80, DEF_STR( "Unknown") );
		PORT_DIPSETTING(    0xc0, "Easy" );	PORT_DIPSETTING(    0x80, "Normal" );	PORT_DIPSETTING(    0x40, "Hard" );	PORT_DIPSETTING(    0x00, "Hardest" );
		PORT_START(); 	/* IN0 */
	    START_COINS();
		PORT_BIT( 0x20, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN1 */
	    JOYSTICK_1();
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );
		PORT_START(); 	/* IN2 */
	    JOYSTICK_2();
		PORT_BIT( 0x40, IP_ACTIVE_HIGH, IPT_UNKNOWN );	PORT_BIT( 0x80, IP_ACTIVE_HIGH, IPT_UNKNOWN );INPUT_PORTS_END(); }}; 
	
	/***************************************************************************
	
		Graphics Decoding
	
	***************************************************************************/
	
	static GfxLayout charlayout = new GfxLayout
	(
		8,8,           /* 8*8 characters */
		256,           /* 256 characters */
		2,             /* 2 bits per pixel */
		new int[] { 0, 4 },
		new int[] { 0, 1, 2, 3, 8*8+0, 8*8+1, 8*8+2, 8*8+3 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		16*8           /* every char takes 16 bytes */
	);
	
	static GfxLayout spritelayout = new GfxLayout
	(
		16,16,	/* 16*16 sprites */
		1024,	/* 1024 sprites */
		3,		/* 3 bits per pixel */
		new int[] { 0*1024*32*8, 1*1024*32*8, 2*1024*32*8 },	/* the bitplanes are separated */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7,
			8*8+0, 8*8+1, 8*8+2, 8*8+3, 8*8+4, 8*8+5, 8*8+6, 8*8+7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8,
				16*8, 17*8, 18*8, 19*8, 20*8, 21*8, 22*8, 23*8 },
		32*8	/* every sprite takes 32 consecutive bytes */
	);
	
	static GfxLayout tilelayout = new GfxLayout
	(
		8,8,	/* 16*16 tiles */
		1024,	/* 1024 tiles */
		3,		/* 3 bits per pixel */
		new int[] { 0*1024*8*8, 1*1024*8*8, 2*1024*8*8 },	/* the bitplanes are separated */
		new int[] { 0, 1, 2, 3, 4, 5, 6, 7 },
		new int[] { 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8 },
		8*8	/* every tile takes 32 consecutive bytes */
	);
	
	static GfxDecodeInfo gfxdecodeinfo[] =
	{
		new GfxDecodeInfo( REGION_GFX1, 0, charlayout,		0, 64 ),
		new GfxDecodeInfo( REGION_GFX2, 0, spritelayout,	0, 32 ),
		new GfxDecodeInfo( REGION_GFX3, 0, tilelayout,		0, 32 ),
		new GfxDecodeInfo( -1 ) /* end of array */
	};
	
	
	
	/***************************************************************************
	
		Sound Interface
	
	***************************************************************************/
	
	static AY8910interface ay8910_interface = new AY8910interface
	(
		2,      /* 2 chips */
		1500000,        /* 1.5 MHz ? */
		new int[] { 30, 30 },
		new ReadHandlerPtr[] { input_port_0_r, null },
		new ReadHandlerPtr[] { input_port_1_r, null },
		new WriteHandlerPtr[] { null, null },
		new WriteHandlerPtr[] { null, null }
	);
	
	/***************************************************************************
	
		Machine Driver
	
	***************************************************************************/
	
	//#define Machine_Driver( name ) 
	//Machine_Driver( gyrodine )
        static MachineDriver machine_driver_gyrodine = new MachineDriver
	(																							
		new MachineCPU[] {																						
			new MachineCPU(																					
				CPU_Z80,																		
				18432000 / 4,	/* 18.432 MHz crystal */										
				gyrodine_readmem,gyrodine_writemem,null,gyrodine_writeport,								
				nmi_interrupt,1																	
			),																					
			new MachineCPU(																					
				CPU_Z80,																		
				18432000 / 4,	/* 18.432 MHz crystal */										
				gyrodine_sub_readmem,gyrodine_sub_writemem,gyrodine_sub_readport,gyrodine_sub_writeport,
				interrupt,4																		
			)																					
		},																						
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */		
		100,	/* cpu interleaving */															
		null,																						
																								
		/* video hardware */																	
		64*8, 32*8, new rectangle( 0*8, 36*8-1, 2*8, 30*8-1 ),												
		gfxdecodeinfo,																			
		256, 0,																					
		palette_RRRR_GGGG_BBBB_convert_prom,													
																								
		VIDEO_TYPE_RASTER,																		
		null,																						
		generic_vh_start,																		
		generic_vh_stop,																		
		kyugo_vh_screenrefresh,																	
																								
		/* sound hardware */																	
		0,0,0,0,																				
		new MachineSound[] {																						
			new MachineSound(																					
				SOUND_AY8910,																	
				ay8910_interface																
			)																					
		}																						
	);
	//Machine_Driver( sonofphx )
        static MachineDriver machine_driver_sonofphx = new MachineDriver
	(																							
		new MachineCPU[] {																						
			new MachineCPU(																					
				CPU_Z80,																		
				18432000 / 4,	/* 18.432 MHz crystal */										
				sonofphx_readmem,sonofphx_writemem,null,sonofphx_writeport,								
				nmi_interrupt,1																	
			),																					
			new MachineCPU(																					
				CPU_Z80,																		
				18432000 / 4,	/* 18.432 MHz crystal */										
				sonofphx_sub_readmem,sonofphx_sub_writemem,sonofphx_sub_readport,sonofphx_sub_writeport,
				interrupt,4																		
			)																					
		},																						
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */		
		100,	/* cpu interleaving */															
		null,																						
																								
		/* video hardware */																	
		64*8, 32*8, new rectangle( 0*8, 36*8-1, 2*8, 30*8-1 ),												
		gfxdecodeinfo,																			
		256, 0,																					
		palette_RRRR_GGGG_BBBB_convert_prom,													
																								
		VIDEO_TYPE_RASTER,																		
		null,																						
		generic_vh_start,																		
		generic_vh_stop,																		
		kyugo_vh_screenrefresh,																	
																								
		/* sound hardware */																	
		0,0,0,0,																				
		new MachineSound[] {																						
			new MachineSound(																					
				SOUND_AY8910,																	
				ay8910_interface																
			)																					
		}																						
	);
	//Machine_Driver( srdmissn )
        static MachineDriver machine_driver_srdmissn = new MachineDriver
	(																							
		new MachineCPU[] {																						
			new MachineCPU(																					
				CPU_Z80,																		
				18432000 / 4,	/* 18.432 MHz crystal */										
				srdmissn_readmem,srdmissn_writemem,null,srdmissn_writeport,								
				nmi_interrupt,1																	
			),																					
			new MachineCPU(																					
				CPU_Z80,																		
				18432000 / 4,	/* 18.432 MHz crystal */										
				srdmissn_sub_readmem,srdmissn_sub_writemem,srdmissn_sub_readport,srdmissn_sub_writeport,
				interrupt,4																		
			)																					
		},																						
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */		
		100,	/* cpu interleaving */															
		null,																						
																								
		/* video hardware */																	
		64*8, 32*8, new rectangle( 0*8, 36*8-1, 2*8, 30*8-1 ),												
		gfxdecodeinfo,																			
		256, 0,																					
		palette_RRRR_GGGG_BBBB_convert_prom,													
																								
		VIDEO_TYPE_RASTER,																		
		null,																						
		generic_vh_start,																		
		generic_vh_stop,																		
		kyugo_vh_screenrefresh,																	
																								
		/* sound hardware */																	
		0,0,0,0,																				
		new MachineSound[] {																						
			new MachineSound(																					
				SOUND_AY8910,																	
				ay8910_interface																
			)																					
		}																						
	);
	//Machine_Driver( flashgal )
        static MachineDriver machine_driver_flashgal = new MachineDriver
	(																							
		new MachineCPU[] {																						
			new MachineCPU(																					
				CPU_Z80,																		
				18432000 / 4,	/* 18.432 MHz crystal */										
				flashgal_readmem,flashgal_writemem,null,flashgal_writeport,								
				nmi_interrupt,1																	
			),																					
			new MachineCPU(																					
				CPU_Z80,																		
				18432000 / 4,	/* 18.432 MHz crystal */										
				flashgal_sub_readmem,flashgal_sub_writemem,flashgal_sub_readport,flashgal_sub_writeport,
				interrupt,4																		
			)																					
		},																						
		60, DEFAULT_REAL_60HZ_VBLANK_DURATION,	/* frames per second, vblank duration */		
		100,	/* cpu interleaving */															
		null,																						
																								
		/* video hardware */																	
		64*8, 32*8, new rectangle( 0*8, 36*8-1, 2*8, 30*8-1 ),												
		gfxdecodeinfo,																			
		256, 0,																					
		palette_RRRR_GGGG_BBBB_convert_prom,													
																								
		VIDEO_TYPE_RASTER,																		
		null,																						
		generic_vh_start,																		
		generic_vh_stop,																		
		kyugo_vh_screenrefresh,																	
																								
		/* sound hardware */																	
		0,0,0,0,																				
		new MachineSound[] {																						
			new MachineSound(																					
				SOUND_AY8910,																	
				ay8910_interface																
			)																					
		}																						
	);
	
	/***************************************************************************
	
	  Game ROMs
	
	***************************************************************************/
	
	static RomLoadPtr rom_gyrodine = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "a21.02", 0x0000, 0x2000, 0xc5ec4a50 );	ROM_LOAD( "a21.03", 0x2000, 0x2000, 0x4e9323bd );	ROM_LOAD( "a21.04", 0x4000, 0x2000, 0x57e659d4 );	ROM_LOAD( "a21.05", 0x6000, 0x2000, 0x1e7293f3 );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for code */
		ROM_LOAD( "a21.01", 0x0000, 0x2000, 0xb2ce0aa2 );
		ROM_REGION( 0x01000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "a21.15", 0x00000, 0x1000, 0xadba18d0 );/* chars */
	
		ROM_REGION( 0x18000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "a21.14", 0x00000, 0x2000, 0x9c5c4d5b );/* sprites - plane 0 */
		/* 0x03000-0x04fff empty */
		ROM_LOAD( "a21.13", 0x04000, 0x2000, 0xd36b5aad );/* sprites - plane 0 */
		/* 0x07000-0x08fff empty */
		ROM_LOAD( "a21.12", 0x08000, 0x2000, 0xf387aea2 );/* sprites - plane 1 */
		/* 0x0b000-0x0cfff empty */
		ROM_LOAD( "a21.11", 0x0c000, 0x2000, 0x87967d7d );/* sprites - plane 1 */
		/* 0x0f000-0x10fff empty */
		ROM_LOAD( "a21.10", 0x10000, 0x2000, 0x59640ab4 );/* sprites - plane 2 */
		/* 0x13000-0x14fff empty */
		ROM_LOAD( "a21.09", 0x14000, 0x2000, 0x22ad88d8 );/* sprites - plane 2 */
		/* 0x17000-0x18fff empty */
	
		ROM_REGION( 0x06000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "a21.08", 0x00000, 0x2000, 0xa57df1c9 );/* tiles - plane 0 */
		ROM_LOAD( "a21.07", 0x02000, 0x2000, 0x63623ba3 );/* tiles - plane 1 */
		ROM_LOAD( "a21.06", 0x04000, 0x2000, 0x4cc969a9 );/* tiles - plane 2 */
	
		ROM_REGION( 0x0340, REGION_PROMS, 0 );	ROM_LOAD( "a21.16", 0x0000, 0x0100, 0xcc25fb56 );/* red */
		ROM_LOAD( "a21.17", 0x0100, 0x0100, 0xca054448 );/* green */
		ROM_LOAD( "a21.18", 0x0200, 0x0100, 0x23c0c449 );/* blue */
		ROM_LOAD( "a21.20", 0x0300, 0x0020, 0xefc4985e );/* char lookup table */
		ROM_LOAD( "a21.19", 0x0320, 0x0020, 0x83a39201 );/* timing? (not used) */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_sonofphx = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "5.f4",   0x0000, 0x2000, 0xe0d2c6cf );	ROM_LOAD( "6.h4",   0x2000, 0x2000, 0x3a0d0336 );	ROM_LOAD( "7.j4",   0x4000, 0x2000, 0x57a8e900 );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for code */
		ROM_LOAD( "1.f2",   0x0000, 0x2000, 0xc485c621 );	ROM_LOAD( "2.h2",   0x2000, 0x2000, 0xb3c6a886 );	ROM_LOAD( "3.j2",   0x4000, 0x2000, 0x197e314c );	ROM_LOAD( "4.k2",   0x6000, 0x2000, 0x4f3695a1 );
		ROM_REGION( 0x01000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "14.4a",  0x00000, 0x1000, 0xb3859b8b );/* chars */
	
		ROM_REGION( 0x18000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "8.6a",   0x00000, 0x4000, 0x0e9f757e );/* sprites - plane 0 */
		ROM_LOAD( "9.7a",   0x04000, 0x4000, 0xf7d2e650 );/* sprites - plane 0 */
		ROM_LOAD( "10.8a",  0x08000, 0x4000, 0xe717baf4 );/* sprites - plane 1 */
		ROM_LOAD( "11.9a",  0x0c000, 0x4000, 0x04b2250b );/* sprites - plane 1 */
		ROM_LOAD( "12.10a", 0x10000, 0x4000, 0xd110e140 );/* sprites - plane 2 */
		ROM_LOAD( "13.11a", 0x14000, 0x4000, 0x8fdc713c );/* sprites - plane 2 */
	
		ROM_REGION( 0x06000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "15.9h",  0x00000, 0x2000, 0xc9213469 );/* tiles - plane 0 */
		ROM_LOAD( "16.10h", 0x02000, 0x2000, 0x7de5d39e );/* tiles - plane 1 */
		ROM_LOAD( "17.11h", 0x04000, 0x2000, 0x0ba5f72c );/* tiles - plane 2 */
	
		ROM_REGION( 0x0340, REGION_PROMS, 0 );	ROM_LOAD( "r.1f",   0x0200, 0x0100, 0xb7f48b41 );/* red */
		ROM_LOAD( "g.1h",   0x0100, 0x0100, 0xacd7a69e );/* green */
		ROM_LOAD( "b.1j",   0x0000, 0x0100, 0x3ea35431 );/* blue */
		/* 0x0300-0x031f empty - looks like there isn't a lookup table PROM */
		ROM_LOAD( "2c",     0x0320, 0x0020, 0x83a39201 );/* timing? (not used) */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_repulse = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "repulse.b5",   0x0000, 0x2000, 0xfb2b7c9d );	ROM_LOAD( "repulse.b6",   0x2000, 0x2000, 0x99129918 );	ROM_LOAD( "7.j4",         0x4000, 0x2000, 0x57a8e900 );
		ROM_REGION( 0x10000, REGION_CPU2 , 0 );/* 64k for code */
		ROM_LOAD( "1.f2",         0x0000, 0x2000, 0xc485c621 );	ROM_LOAD( "2.h2",         0x2000, 0x2000, 0xb3c6a886 );	ROM_LOAD( "3.j2",         0x4000, 0x2000, 0x197e314c );	ROM_LOAD( "repulse.b4",   0x6000, 0x2000, 0x86b267f3 );
		ROM_REGION( 0x01000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "repulse.a11",  0x00000, 0x1000, 0x8e1de90a );/* chars */
	
		ROM_REGION( 0x18000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "8.6a",         0x00000, 0x4000, 0x0e9f757e );/* sprites - plane 0 */
		ROM_LOAD( "9.7a",         0x04000, 0x4000, 0xf7d2e650 );/* sprites - plane 0 */
		ROM_LOAD( "10.8a",        0x08000, 0x4000, 0xe717baf4 );/* sprites - plane 1 */
		ROM_LOAD( "11.9a",        0x0c000, 0x4000, 0x04b2250b );/* sprites - plane 1 */
		ROM_LOAD( "12.10a",       0x10000, 0x4000, 0xd110e140 );/* sprites - plane 2 */
		ROM_LOAD( "13.11a",       0x14000, 0x4000, 0x8fdc713c );/* sprites - plane 2 */
	
		ROM_REGION( 0x06000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "15.9h",        0x00000, 0x2000, 0xc9213469 );/* tiles - plane 0 */
		ROM_LOAD( "16.10h",       0x02000, 0x2000, 0x7de5d39e );/* tiles - plane 1 */
		ROM_LOAD( "17.11h",       0x04000, 0x2000, 0x0ba5f72c );/* tiles - plane 2 */
	
		ROM_REGION( 0x0340, REGION_PROMS, 0 );	ROM_LOAD( "r.1f",         0x0200, 0x0100, 0xb7f48b41 );/* red */
		ROM_LOAD( "g.1h",         0x0100, 0x0100, 0xacd7a69e );/* green */
		ROM_LOAD( "b.1j",         0x0000, 0x0100, 0x3ea35431 );/* blue */
		/* 0x0300-0x031f empty - looks like there isn't a lookup table PROM */
		ROM_LOAD( "2c",           0x0320, 0x0020, 0x83a39201 );/* timing? (not used) */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_99lstwar = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "1999.4f",      0x0000, 0x2000, 0xe3cfc09f );	ROM_LOAD( "1999.4h",      0x2000, 0x2000, 0xfd58c6e1 );	ROM_LOAD( "7.j4",         0x4000, 0x2000, 0x57a8e900 );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for code */
		ROM_LOAD( "1.f2",         0x0000, 0x2000, 0xc485c621 );	ROM_LOAD( "2.h2",         0x2000, 0x2000, 0xb3c6a886 );	ROM_LOAD( "3.j2",         0x4000, 0x2000, 0x197e314c );	ROM_LOAD( "repulse.b4",   0x6000, 0x2000, 0x86b267f3 );
		ROM_REGION( 0x01000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "1999.4a",      0x00000, 0x1000, 0x49a2383e );/* chars */
	
		ROM_REGION( 0x18000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "8.6a",         0x00000, 0x4000, 0x0e9f757e );/* sprites - plane 0 */
		ROM_LOAD( "9.7a",         0x04000, 0x4000, 0xf7d2e650 );/* sprites - plane 0 */
		ROM_LOAD( "10.8a",        0x08000, 0x4000, 0xe717baf4 );/* sprites - plane 1 */
		ROM_LOAD( "11.9a",        0x0c000, 0x4000, 0x04b2250b );/* sprites - plane 1 */
		ROM_LOAD( "12.10a",       0x10000, 0x4000, 0xd110e140 );/* sprites - plane 2 */
		ROM_LOAD( "13.11a",       0x14000, 0x4000, 0x8fdc713c );/* sprites - plane 2 */
	
		ROM_REGION( 0x06000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "15.9h",        0x00000, 0x2000, 0xc9213469 );/* tiles - plane 0 */
		ROM_LOAD( "16.10h",       0x02000, 0x2000, 0x7de5d39e );/* tiles - plane 1 */
		ROM_LOAD( "17.11h",       0x04000, 0x2000, 0x0ba5f72c );/* tiles - plane 2 */
	
		ROM_REGION( 0x0340, REGION_PROMS, 0 );	ROM_LOAD( "r.1f",         0x0200, 0x0100, 0xb7f48b41 );/* red */
		ROM_LOAD( "g.1h",         0x0100, 0x0100, 0xacd7a69e );/* green */
		ROM_LOAD( "b.1j",         0x0000, 0x0100, 0x3ea35431 );/* blue */
		/* 0x0300-0x031f empty - looks like there isn't a lookup table PROM */
		ROM_LOAD( "2c",           0x0320, 0x0020, 0x83a39201 );/* timing? (not used) */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_99lstwra = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "4f.bin",       0x0000, 0x2000, 0xefe2908d );	ROM_LOAD( "4h.bin",       0x2000, 0x2000, 0x5b79c342 );	ROM_LOAD( "4j.bin",       0x4000, 0x2000, 0xd2a62c1b );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for code */
		ROM_LOAD( "2f.bin",       0x0000, 0x2000, 0xcb9d8291 );	ROM_LOAD( "2h.bin",       0x2000, 0x2000, 0x24dbddc3 );	ROM_LOAD( "2j.bin",       0x4000, 0x2000, 0x16879c4c );	ROM_LOAD( "repulse.b4",   0x6000, 0x2000, 0x86b267f3 );
		ROM_REGION( 0x01000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "1999.4a",      0x00000, 0x1000, 0x49a2383e );/* chars */
	
		ROM_REGION( 0x18000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "6a.bin",       0x00000, 0x4000, 0x98d44410 );/* sprites - plane 0 */
		ROM_LOAD( "7a.bin",       0x04000, 0x4000, 0x4c54d281 );/* sprites - plane 0 */
		ROM_LOAD( "8a.bin",       0x08000, 0x4000, 0x81018101 );/* sprites - plane 1 */
		ROM_LOAD( "9a.bin",       0x0c000, 0x4000, 0x347b91fd );/* sprites - plane 1 */
		ROM_LOAD( "10a.bin",      0x10000, 0x4000, 0xf07de4fa );/* sprites - plane 2 */
		ROM_LOAD( "11a.bin",      0x14000, 0x4000, 0x34a04f48 );/* sprites - plane 2 */
	
		ROM_REGION( 0x06000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "9h.bin",       0x00000, 0x2000, 0x59993c27 );/* tiles - plane 0 */
		ROM_LOAD( "10h.bin",      0x02000, 0x2000, 0xdfbf0280 );/* tiles - plane 1 */
		ROM_LOAD( "11h.bin",      0x04000, 0x2000, 0xe4f29fc0 );/* tiles - plane 2 */
	
		ROM_REGION( 0x0340, REGION_PROMS, 0 );	ROM_LOAD( "r.1f",         0x0200, 0x0100, 0xb7f48b41 );/* red */
		ROM_LOAD( "g.1h",         0x0100, 0x0100, 0xacd7a69e );/* green */
		ROM_LOAD( "b.1j",         0x0000, 0x0100, 0x3ea35431 );/* blue */
		/* 0x0300-0x031f empty - looks like there isn't a lookup table PROM */
		ROM_LOAD( "2c",           0x0320, 0x0020, 0x83a39201 );/* timing? (not used) */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_flashgal = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "15.4f",        0x0000, 0x2000, 0xcf5ad733 );	ROM_LOAD( "16.4h",        0x2000, 0x2000, 0x00c4851f );	ROM_LOAD( "17.4j",        0x4000, 0x2000, 0x1ef0b8f7 );	ROM_LOAD( "18.4k",        0x6000, 0x2000, 0x885d53de );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for code */
		ROM_LOAD( "11.2f",        0x0000, 0x2000, 0xeee2134d );	ROM_LOAD( "12.2h",        0x2000, 0x2000, 0xe5e0cd22 );	ROM_LOAD( "13.2j",        0x4000, 0x2000, 0x4cd3fe5e );	ROM_LOAD( "14.2k",        0x6000, 0x2000, 0x552ca339 );
		ROM_REGION( 0x01000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "19.4b",        0x00000, 0x1000, 0xdca9052f );/* chars */
	
		ROM_REGION( 0x18000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "20.6b",        0x00000, 0x4000, 0x62caf2a1 );/* sprites - plane 0 */
		ROM_LOAD( "21.7b",        0x04000, 0x4000, 0x10f78a10 );/* sprites - plane 0 */
		ROM_LOAD( "22.8b",        0x08000, 0x4000, 0x36ea1d59 );/* sprites - plane 1 */
		ROM_LOAD( "23.9b",        0x0c000, 0x4000, 0xf527d837 );/* sprites - plane 1 */
		ROM_LOAD( "24.10b",       0x10000, 0x4000, 0xba76e4c1 );/* sprites - plane 2 */
		ROM_LOAD( "25.11b",       0x14000, 0x4000, 0xf095d619 );/* sprites - plane 2 */
	
		ROM_REGION( 0x06000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "26.9h",        0x00000, 0x2000, 0x2f5b62c0 );/* tiles - plane 0 */
		ROM_LOAD( "27.10h",       0x02000, 0x2000, 0x8fbb49b5 );/* tiles - plane 1 */
		ROM_LOAD( "28.11h",       0x04000, 0x2000, 0x26a8e5c3 );/* tiles - plane 2 */
	
		ROM_REGION( 0x0340, REGION_PROMS, 0 );	ROM_LOAD( "flashgal.prr", 0x0000, 0x0100, 0x02c4043f );/* red */
		ROM_LOAD( "flashgal.prg", 0x0100, 0x0100, 0x225938d1 );/* green */
		ROM_LOAD( "flashgal.prb", 0x0200, 0x0100, 0x1e0a1cd3 );/* blue */
		ROM_LOAD( "flashgal.pr2", 0x0300, 0x0020, 0xcce2e29f );/* char lookup table */
		ROM_LOAD( "flashgal.pr1", 0x0320, 0x0020, 0x83a39201 );/* timing? (not used) */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_srdmissn = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "5.t2",   0x0000, 0x4000, 0xa682b48c );	ROM_LOAD( "7.t3",   0x4000, 0x4000, 0x1719c58c );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for code */
		ROM_LOAD( "1.t7",   0x0000, 0x4000, 0xdc48595e );	ROM_LOAD( "3.t8",   0x4000, 0x4000, 0x216be1e8 );
		ROM_REGION( 0x01000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "15.4a",  0x00000, 0x1000, 0x4961f7fd );/* chars */
	
		ROM_REGION( 0x18000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "14.6a",  0x00000, 0x4000, 0x3d4c0447 );/* sprites - plane 0 */
		ROM_LOAD( "13.7a",  0x04000, 0x4000, 0x22414a67 );/* sprites - plane 0 */
		ROM_LOAD( "12.8a",  0x08000, 0x4000, 0x61e34283 );/* sprites - plane 1 */
		ROM_LOAD( "11.9a",  0x0c000, 0x4000, 0xbbbaffef );/* sprites - plane 1 */
		ROM_LOAD( "10.10a", 0x10000, 0x4000, 0xde564f97 );/* sprites - plane 2 */
		ROM_LOAD( "9.11a",  0x14000, 0x4000, 0x890dc815 );/* sprites - plane 2 */
	
		ROM_REGION( 0x06000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "17.9h",  0x00000, 0x2000, 0x41211458 );/* tiles - plane 1 */
		ROM_LOAD( "18.10h", 0x02000, 0x2000, 0x740eccd4 );/* tiles - plane 0 */
		ROM_LOAD( "16.11h", 0x04000, 0x2000, 0xc1f4a5db );/* tiles - plane 2 */
	
		ROM_REGION( 0x0340, REGION_PROMS, 0 );	ROM_LOAD( "mr.1j",  0x0000, 0x0100, 0x110a436e );/* red */
		ROM_LOAD( "mg.1h",  0x0100, 0x0100, 0x0fbfd9f0 );/* green */
		ROM_LOAD( "mb.1f",  0x0200, 0x0100, 0xa342890c );/* blue */
		ROM_LOAD( "m2.5j",  0x0300, 0x0020, 0x190a55ad );/* char lookup table */
		ROM_LOAD( "m1.2c",  0x0320, 0x0020, 0x83a39201 );/* timing? not used */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_airwolf = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "b.2s",        0x0000, 0x8000, 0x8c993cce );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for code */
		ROM_LOAD( "a.7s",        0x0000, 0x8000, 0xa3c7af5c );
		ROM_REGION( 0x01000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "f.4a",        0x00000, 0x1000, 0x4df44ce9 );/* chars */
	
		ROM_REGION( 0x18000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "e.6a",        0x00000, 0x2000, 0xe8fbc7d2 );/* sprites - plane 0 */
		ROM_CONTINUE(            0x04000, 0x2000 );	ROM_CONTINUE(            0x02000, 0x2000 );	ROM_CONTINUE(            0x06000, 0x2000 );	ROM_LOAD( "d.8a",        0x08000, 0x2000, 0xc5d4156b );/* sprites - plane 1 */
		ROM_CONTINUE(            0x0c000, 0x2000 );	ROM_CONTINUE(            0x0a000, 0x2000 );	ROM_CONTINUE(            0x0e000, 0x2000 );	ROM_LOAD( "c.10a",       0x10000, 0x2000, 0xde91dfb1 );/* sprites - plane 2 */
		ROM_CONTINUE(            0x14000, 0x2000 );	ROM_CONTINUE(            0x12000, 0x2000 );	ROM_CONTINUE(            0x16000, 0x2000 );
		ROM_REGION( 0x06000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "09h_14.bin",  0x00000, 0x2000, 0x25e57e1f );/* tiles - plane 1 */
		ROM_LOAD( "10h_13.bin",  0x02000, 0x2000, 0xcf0de5e9 );/* tiles - plane 0 */
		ROM_LOAD( "11h_12.bin",  0x04000, 0x2000, 0x4050c048 );/* tiles - plane 2 */
	
		ROM_REGION( 0x0340, REGION_PROMS, 0 );	ROM_LOAD( "01j.bin",     0x0000, 0x0100, 0x6a94b2a3 );/* red */
		ROM_LOAD( "01h.bin",     0x0100, 0x0100, 0xec0923d3 );/* green */
		ROM_LOAD( "01f.bin",     0x0200, 0x0100, 0xade97052 );/* blue */
		/* 0x0300-0x031f empty - looks like there isn't a lookup table PROM */
		ROM_LOAD( "02c_m1.bin",  0x0320, 0x0020, 0x83a39201 );/* timing? not used */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_skywolf = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "02s_03.bin",  0x0000, 0x4000, 0xa0891798 );	ROM_LOAD( "03s_04.bin",  0x4000, 0x4000, 0x5f515d46 );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for code */
		ROM_LOAD( "07s_01.bin",  0x0000, 0x4000, 0xc680a905 );	ROM_LOAD( "08s_02.bin",  0x4000, 0x4000, 0x3d66bf26 );
		ROM_REGION( 0x01000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "04a_11.bin",  0x00000, 0x1000, 0x219de9aa );/* chars */
	
		ROM_REGION( 0x18000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "06a_10.bin",  0x00000, 0x4000, 0x1c809383 );/* sprites - plane 0 */
		ROM_LOAD( "07a_09.bin",  0x04000, 0x4000, 0x5665d774 );/* sprites - plane 0 */
		ROM_LOAD( "08a_08.bin",  0x08000, 0x4000, 0x6dda8f2a );/* sprites - plane 1 */
		ROM_LOAD( "09a_07.bin",  0x0c000, 0x4000, 0x6a21ddb8 );/* sprites - plane 1 */
		ROM_LOAD( "10a_06.bin",  0x10000, 0x4000, 0xf2e548e0 );/* sprites - plane 2 */
		ROM_LOAD( "11a_05.bin",  0x14000, 0x4000, 0x8681b112 );/* sprites - plane 2 */
	
		ROM_REGION( 0x06000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "09h_14.bin",  0x00000, 0x2000, 0x25e57e1f );/* tiles - plane 1 */
		ROM_LOAD( "10h_13.bin",  0x02000, 0x2000, 0xcf0de5e9 );/* tiles - plane 0 */
		ROM_LOAD( "11h_12.bin",  0x04000, 0x2000, 0x4050c048 );/* tiles - plane 2 */
	
		ROM_REGION( 0x0340, REGION_PROMS, 0 );	ROM_LOAD( "01j.bin",     0x0000, 0x0100, 0x6a94b2a3 );/* red */
		ROM_LOAD( "01h.bin",     0x0100, 0x0100, 0xec0923d3 );/* green */
		ROM_LOAD( "01f.bin",     0x0200, 0x0100, 0xade97052 );/* blue */
		/* 0x0300-0x031f empty - looks like there isn't a lookup table PROM */
		ROM_LOAD( "02c_m1.bin",  0x0320, 0x0020, 0x83a39201 );/* timing? not used */
	ROM_END(); }}; 
	
	static RomLoadPtr rom_skywolf2 = new RomLoadPtr(){ public void handler(){ 
		ROM_REGION( 0x10000, REGION_CPU1, 0 );/* 64k for code */
		ROM_LOAD( "z80_2.bin",   0x0000, 0x8000, 0x34db7bda );
		ROM_REGION( 0x10000, REGION_CPU2, 0 );/* 64k for code */
		ROM_LOAD( "07s_01.bin",  0x0000, 0x4000, 0xc680a905 );	ROM_LOAD( "08s_02.bin",  0x4000, 0x4000, 0x3d66bf26 );
		ROM_REGION( 0x01000, REGION_GFX1, ROMREGION_DISPOSE );	ROM_LOAD( "04a_11.bin",  0x00000, 0x1000, 0x219de9aa );/* chars */
	
		ROM_REGION( 0x18000, REGION_GFX2, ROMREGION_DISPOSE );	ROM_LOAD( "06a_10.bin",  0x00000, 0x4000, 0x1c809383 );/* sprites - plane 0 */
		ROM_LOAD( "07a_09.bin",  0x04000, 0x4000, 0x5665d774 );/* sprites - plane 0 */
		ROM_LOAD( "08a_08.bin",  0x08000, 0x4000, 0x6dda8f2a );/* sprites - plane 1 */
		ROM_LOAD( "09a_07.bin",  0x0c000, 0x4000, 0x6a21ddb8 );/* sprites - plane 1 */
		ROM_LOAD( "10a_06.bin",  0x10000, 0x4000, 0xf2e548e0 );/* sprites - plane 2 */
		ROM_LOAD( "11a_05.bin",  0x14000, 0x4000, 0x8681b112 );/* sprites - plane 2 */
	
		ROM_REGION( 0x06000, REGION_GFX3, ROMREGION_DISPOSE );	ROM_LOAD( "09h_14.bin",  0x00000, 0x2000, 0x25e57e1f );/* tiles - plane 1 */
		ROM_LOAD( "10h_13.bin",  0x02000, 0x2000, 0xcf0de5e9 );/* tiles - plane 0 */
		ROM_LOAD( "11h_12.bin",  0x04000, 0x2000, 0x4050c048 );/* tiles - plane 2 */
	
		ROM_REGION( 0x0340, REGION_PROMS, 0 );	ROM_LOAD( "01j.bin",     0x0000, 0x0100, 0x6a94b2a3 );/* red */
		ROM_LOAD( "01h.bin",     0x0100, 0x0100, 0xec0923d3 );/* green */
		ROM_LOAD( "01f.bin",     0x0200, 0x0100, 0xade97052 );/* blue */
		/* 0x0300-0x031f empty - looks like there isn't a lookup table PROM */
		ROM_LOAD( "02c_m1.bin",  0x0320, 0x0020, 0x83a39201 );/* timing? not used */
	ROM_END(); }}; 
	
	
	/***************************************************************************
	
	  Game driver(s)
	
	***************************************************************************/
	
	public static GameDriver driver_gyrodine	   = new GameDriver("1984"	,"gyrodine"	,"kyugo.java"	,rom_gyrodine,null	,machine_driver_gyrodine	,input_ports_gyrodine	,null	,ROT90	,	"Taito Corporation", "Gyrodine" );
	public static GameDriver driver_sonofphx	   = new GameDriver("1985"	,"sonofphx"	,"kyugo.java"	,rom_sonofphx,null	,machine_driver_sonofphx	,input_ports_sonofphx	,null	,ROT90	,	"Associated Overseas MFR, Inc", "Son of Phoenix" );
	public static GameDriver driver_repulse	   = new GameDriver("1985"	,"repulse"	,"kyugo.java"	,rom_repulse,driver_sonofphx	,machine_driver_sonofphx	,input_ports_sonofphx	,null	,ROT90	,	"Sega", "Repulse" );
	public static GameDriver driver_99lstwar	   = new GameDriver("1985"	,"99lstwar"	,"kyugo.java"	,rom_99lstwar,driver_sonofphx	,machine_driver_sonofphx	,input_ports_sonofphx	,null	,ROT90	,	"Proma", "'99 The Last War" );
	public static GameDriver driver_99lstwra	   = new GameDriver("1985"	,"99lstwra"	,"kyugo.java"	,rom_99lstwra,driver_sonofphx	,machine_driver_sonofphx	,input_ports_sonofphx	,null	,ROT90	,	"Proma", "'99 The Last War (alternate)" );
	public static GameDriver driver_flashgal	   = new GameDriver("1985"	,"flashgal"	,"kyugo.java"	,rom_flashgal,null	,machine_driver_flashgal	,input_ports_flashgal	,null	,ROT0	,	"Sega", "Flash Gal" );
	public static GameDriver driver_srdmissn	   = new GameDriver("1986"	,"srdmissn"	,"kyugo.java"	,rom_srdmissn,null	,machine_driver_srdmissn	,input_ports_srdmissn	,null	,ROT90	,	"Taito Corporation", "S.R.D. Mission" );
	public static GameDriver driver_airwolf	   = new GameDriver("1987"	,"airwolf"	,"kyugo.java"	,rom_airwolf,null	,machine_driver_srdmissn	,input_ports_airwolf	,null	,ROT0	,	"Kyugo", "Air Wolf" );
	public static GameDriver driver_skywolf	   = new GameDriver("1987"	,"skywolf"	,"kyugo.java"	,rom_skywolf,driver_airwolf	,machine_driver_srdmissn	,input_ports_skywolf	,null	,ROT0	,	"bootleg", "Sky Wolf (set 1)" );
	public static GameDriver driver_skywolf2	   = new GameDriver("1987"	,"skywolf2"	,"kyugo.java"	,rom_skywolf2,driver_airwolf	,machine_driver_srdmissn	,input_ports_airwolf	,null	,ROT0	,	"bootleg", "Sky Wolf (set 2)" );
}
