/*************************************************************************

	Atari Slapstic decoding helper

**************************************************************************

	Atari Slapstic FAQ
	Version 1.1
	by Aaron Giles and Frank Palazzolo
	08/06/2001


	What is a slapstic?

	The slapstic was a security chip made by Atari, which was used for
	bank switching and security in several coin-operated video games from
	1984 through 1990.


	What is a SLOOP?

	The SLOOP (or "SLOOPstic") is a follow-on chip to the slapstic. It
	provides a similar type of security, but is programmed onto a GAL6001,
	rather than a custom part. It was created because Atari was running
	out of slapstics to use in their games, and the original masks for the
	slapstic had been lost by the company that manufactured them. A separate
	FAQ for this chip is planned for the future.


	How do I identify a slapstic chip on my board?

	Look for a small, socketed 20-pin DIP on the board. The number on
	the chip will be 137412-1xx.


	Are slapstic chips interchangeable?

	Sadly, no. They were designed to prevent operators from burning
	new EPROMs and "upgrading" their PCBs to a new game without buying
	the necessary kits from Atari. For example, the five System 1 games
	each used a different slapstic, so that you couldn't take, say,
	a <b>Marble Madness</b> machine, burn new EPROMs, and convert it into
	an <b>Indiana Jones</b>.

	That said, however, there are two pairs of the slapstics that appear
	to be functionally identical, despite the fact that they have
	different part numbers:

		137412-103 (<b>Marble Madness</b>) appears to be functionally identical
			to 137412-110 (<b>Road Blasters</b> & <b>APB</b>)

		137412-106 (<b>Gauntlet II</b>) appears to be functionally identical
			to 137412-109 (<b>Championship Sprint</b>)

	Note, however, that I have not tried these swaps to confirm that they
	work. Your mileage may vary.


	How many different slapstics are there?

	All told, a total of 13 actual slapstics have been found. However,
	there are gaps in the numbering which indicate that more may exist.


	Do all slapstics work the same?

	In general, yes. However, matters are complicated by the existence
	of multiple revisions of the chip design:

		SLAPSTIC	Part #137412-101 through 137412-110
		SLAPSTIC-2	Part #137412-111 through 137412-118

	In the simplest case, both revs act the same. However, they differ
	in how the more complex modes of operation are used.


	How is the slapstic connected to the game?

	The slapstic generally sits between the CPU's address bus and one
	of the program ROMs. Here's a pinout:

			A9   1 +-v-+ 20  A8
			A10  2 |   | 19  A7
			A11  3 |   | 18  A6
			A12  4 |   | 17  A5
			A13  5 |   | 16  A4
			/CS  6 |   | 15  A3
			CLK  7 |   | 14  A2
			VCC  8 |   | 13  A1
			BS1  9 |   | 12  A0
			BS0 10 +---+ 11 GND

	A0-A13 are the address lines from the CPU. CLK and /CS together
	trigger a state change. BS0 and BS1 are the bank select outputs,
	which usually connect to the protected program ROM in place of
	two address lines (traditionally A12 and A13).

	Most slapstics were used on 68000 or T-11 based games, which had
	a 16-bit address bus. This meant that A0-A13 on the slapstic were
	generally connected to A1-A14 on the CPU. However, two 8-bit
	games (Tetris and Empire Strikes Back) used the slapstic as well.
	This slapstic (#101) has a slightly different pinout, though it
	operates similarly to the others in its class.

			A8   1 +-v-+ 20  A7
			A9   2 |   | 19  A6
			A10  3 |   | 18  A5
			A11  4 |   | 17  A4
			A12  5 |   | 16  A3
			/CS  6 |   | 15  A2
			CLK  7 |   | 14  A1
			VCC  8 |   | 13  A0
			/BS1 9 |   | 12 GND
			BS1 10 +---+ 11 BS0


	Which games used slapstics?

		137412-101	Empire Strikes Back
		137412-101	Tetris
		137412-103	Marble Madness
		137412-104	Gauntlet
		137412-105	Paperboy
		137412-105	Indiana Jones & the Temple of Doom
		137412-106	Gauntlet II
		137412-107	2-Player Gauntlet
		137412-107	Peter Packrat
		137412-107	720 Degrees
		137412-107	Xybots
		137412-108	Road Runner
		137412-108	Super Sprint
		137412-109	Championship Sprint
		137412-110	Road Blasters
		137412-110	APB
		137412-111	Pit Fighter
		137412-116	Hydra
		137412-116	Tournament Cyberball 2072
		137412-117	Race Drivin'
		137412-118	Rampart
		137412-118	Vindicators Part II


	How does the slapstic work?

	On power-up, the slapstic starts by pointing to bank 0 or bank 3.
	After that, certain sequences of addresses will trigger a bankswitch.
	Each sequence begins with an access to location $0000, followed by one
	or more special addresses.

	Each slapstic has a 'simple' mode of bankswitching, consisting of an
	access to $0000 followed by an access to one of four bank addresses.
	Other accesses are allowed in between these two accesses without
	affecting the outcome.

	Additionally, each slapstic has a trickier variant of the
	bankswitching, which requires an access to $0000, followed by accesses
	to two specific addresses, followed by one of four alternate bank
	addresses. All three accesses following the $0000 must occur in
	sequence with no interruptions, or else the sequence is invalidated.

	Finally, each slapstic has a mechanism for modifying the value of the
	current bank. Earlier chips (101-110) allowed you to twiddle the
	specific bits of the bank number, clearing or setting bits 0 and 1
	independently. Later chips (111-118) provided a mechanism of adding
	1, 2, or 3 to the number of the current bank.

	Surprisingly, the slapstic appears to have used DRAM cells to store
	the current bank. After 5 or 6 seconds without a clock, the chip
	reverts to the default bank, with the chip reset (bank select
	addresses are enabled). Typically, the slapstic region is accessed
	often enough to cause a problem.

	For full details, see the MAME source code.

*************************************************************************/

/*
 * ported to v0.56
 * using automatic conversion tool v0.01
 */ 
package WIP2.mame056.machine;

import static WIP2.mame056.cpuexecH.*;
import static WIP2.mame056.cpuintrfH.*;
import static WIP2.mame056.mame.Machine;

public class slapstic
{
	
	
	/*************************************
	 *
	 *	Structure of slapstic params
	 *
	 *************************************/
	
	public static class mask_value
	{
		public int mask, value;
                
                public mask_value(int mask, int value){
                    this.mask = mask;
                    this.value = value;
                }
	};
	
	
	public static class slapstic_data
	{
		public int bankstart;
		public int[] bank=new int[4];
	
		public mask_value alt1;
		public mask_value alt2;
		public mask_value alt3;
		public int altshift;
	
		public mask_value bit1;
		public mask_value bit2c0;
		public mask_value bit2s0;
		public mask_value bit2c1;
		public mask_value bit2s1;
		public mask_value bit3;
	
		public mask_value add1;
		public mask_value add2;
		public mask_value addplus1;
		public mask_value addplus2;
		public mask_value addplus3;
		public mask_value add3;
                
                public slapstic_data(int bankstart, int[] bank, mask_value alt1, mask_value alt2, mask_value alt3, int altshift, mask_value bit1, mask_value bit2c0, mask_value bit2s0, mask_value bit2c1, mask_value bit2s1, mask_value bit3, mask_value add1, mask_value add2, mask_value addplus1, mask_value addplus2, mask_value addplus3, mask_value add3){
                    this.bankstart = bankstart;
                    this.bank = bank;
	
                    this.alt1 = alt1;
                    this.alt2 = alt2;
                    this.alt3 = alt3;
		
                    this.altshift = altshift;
	
                    this.bit1 = bit1;
                    this.bit2c0 = bit2c0;
                    this.bit2s0 = bit2s0;
                    this.bit2c1 = bit2c1;
                    this.bit2s1 = bit2s1;
                    this.bit3 = bit3;
	
                    this.add1 = add1;
                    this.add2 = add2;
                    this.addplus1 = addplus1;
                    this.addplus2 = addplus2;
                    this.addplus3 = addplus3;
                
                    this.add3 = add3;
                };
	};
	
	
	
	/*************************************
	 *
	 *	Shorthand
	 *
	 *************************************/
	
	public static int UNKNOWN = 0xffff;
	
        public static int[][] NO_BITWISE = {
		{ UNKNOWN,UNKNOWN },
		{ UNKNOWN,UNKNOWN },
		{ UNKNOWN,UNKNOWN },
		{ UNKNOWN,UNKNOWN },
		{ UNKNOWN,UNKNOWN },
		{ UNKNOWN,UNKNOWN }                
        };

	public static int[][] NO_ADDITIVE = {
		{ UNKNOWN,UNKNOWN },
		{ UNKNOWN,UNKNOWN },
		{ UNKNOWN,UNKNOWN },
		{ UNKNOWN,UNKNOWN },
		{ UNKNOWN,UNKNOWN },
		{ UNKNOWN,UNKNOWN }                
        };
	
	
	/*************************************
	 *
	 *	Constants
	 *
	 *************************************/
	
	//static enum state_type
	//{
		public static final int DISABLED=0;
		public static final int ENABLED=1;
		public static final int ALTERNATE1=2;
		public static final int ALTERNATE2=3;
		public static final int ALTERNATE3=4;
		public static final int BITWISE1=5;
		public static final int BITWISE2=6;
		public static final int BITWISE3=7;
		public static final int ADDITIVE1=8;
		public static final int ADDITIVE2=9;
		public static final int ADDITIVE3=10;
	//};
	
	/*TODO*///#define LOG_SLAPSTIC	0
	
	
	
	/*************************************
	 *
	 *	Slapstic definitions
	 *
	 *************************************/
	
	/* slapstic 137412-101: Empire Strikes Back/Tetris (NOT confirmed) */
	static slapstic_data slapstic101 = new slapstic_data
	(
		/* basic banking */
		3,								/* starting bank */
		new int[]{ 0x0080,0x0090,0x00a0,0x00b0 },/* bank select values */
	
		/* alternate banking */
		new mask_value( 0x007f,UNKNOWN ),				/* 1st mask/value in sequence */
		new mask_value( 0x1fff,0x1dfe ),				/* 2nd mask/value in sequence */
		new mask_value( 0x1ffc,0x1b5c ),				/* 3rd mask/value in sequence */
		0,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( 0x1ff0,0x1540 ),				/* 1st mask/value in sequence */
		new mask_value( 0x1ff3,0x1540 ),				/* clear bit 0 value */
		new mask_value( 0x1ff3,0x1541 ),				/*   set bit 0 value */
		new mask_value( 0x1ff3,0x1542 ),				/* clear bit 1 value */
		new mask_value( 0x1ff3,0x1543 ),				/*   set bit 1 value */
		new mask_value( 0x1ff8,0x1550 ),				/* final mask/value in sequence */
	
		/* additive banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN )
	);
	
	
	/* slapstic 137412-103: Marble Madness (confirmed) */
	static slapstic_data slapstic103 = new slapstic_data
        (
		/* basic banking */
		3,								/* starting bank */
		new int[]{ 0x0040,0x0050,0x0060,0x0070 },/* bank select values */
	
		/* alternate banking */
		new mask_value( 0x007f,0x002d ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x3d14 ),				/* 2nd mask/value in sequence */
		new mask_value( 0x3ffc,0x3d24 ),				/* 3rd mask/value in sequence */
		0,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( 0x3ff0,0x34c0 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3ff3,0x34c0 ),				/* clear bit 0 value */
		new mask_value( 0x3ff3,0x34c1 ),				/*   set bit 0 value */
		new mask_value( 0x3ff3,0x34c2 ),				/* clear bit 1 value */
		new mask_value( 0x3ff3,0x34c3 ),				/*   set bit 1 value */
		new mask_value( 0x3ff8,0x34d0 ),				/* final mask/value in sequence */
	
		/* additive banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN )
        );
	
	
	/* slapstic 137412-104: Gauntlet (confirmed) */
	static slapstic_data slapstic104 = new slapstic_data
        (
		/* basic banking */
		3,								/* starting bank */
		new int[]{ 0x0020,0x0028,0x0030,0x0038 },/* bank select values */
	
		/* alternate banking */
		new mask_value( 0x007f,0x0069 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x3735 ),				/* 2nd mask/value in sequence */
		new mask_value( 0x3ffc,0x3764 ),				/* 3rd mask/value in sequence */
		0,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( 0x3ff0,0x3d90 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3ff3,0x3d90 ),				/* clear bit 0 value */
		new mask_value( 0x3ff3,0x3d91 ),				/*   set bit 0 value */
		new mask_value( 0x3ff3,0x3d92 ),				/* clear bit 1 value */
		new mask_value( 0x3ff3,0x3d93 ),				/*   set bit 1 value */
		new mask_value( 0x3ff8,0x3da0 ),				/* final mask/value in sequence */
	
		/* additive banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN )
        );
	
	
	/* slapstic 137412-105: Indiana Jones/Paperboy (confirmed) */
	static slapstic_data slapstic105 = new slapstic_data
        (
		/* basic banking */
		3,								/* starting bank */
		new int[]{ 0x0010,0x0014,0x0018,0x001c },/* bank select values */
	
		/* alternate banking */
		new mask_value( 0x007f,0x003d ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x0092 ),				/* 2nd mask/value in sequence */
		new mask_value( 0x3ffc,0x00a4 ),				/* 3rd mask/value in sequence */
		0,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( 0x3ff0,0x35b0 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3ff3,0x35b0 ),				/* clear bit 0 value */
		new mask_value( 0x3ff3,0x35b1 ),				/*   set bit 0 value */
		new mask_value( 0x3ff3,0x35b2 ),				/* clear bit 1 value */
		new mask_value( 0x3ff3,0x35b3 ),				/*   set bit 1 value */
		new mask_value( 0x3ff8,0x35c0 ),				/* final mask/value in sequence */
	
		/* additive banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN )
        );
	
	
	/* slapstic 137412-106: Gauntlet II (NOT confirmed) */
	static slapstic_data slapstic106 = new slapstic_data
        (
		/* basic banking */
		3,								/* starting bank */
		new int[]{ 0x0008,0x000a,0x000c,0x000e },/* bank select values */
	
		/* alternate banking */
		new mask_value( 0x007f,0x002b ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x0052 ),				/* 2nd mask/value in sequence */
		new mask_value( 0x3ffc,0x0064 ),				/* 3rd mask/value in sequence */
		0,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( 0x3ff0,0x3da0 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3ff3,0x3da0 ),				/* clear bit 0 value */
		new mask_value( 0x3ff3,0x3da1 ),				/*   set bit 0 value */
		new mask_value( 0x3ff3,0x3da2 ),				/* clear bit 1 value */
		new mask_value( 0x3ff3,0x3da3 ),				/*   set bit 1 value */
		new mask_value( 0x3ff8,0x3db0 ),				/* final mask/value in sequence */
	
		/* additive banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN )
        );
	
	
	/* slapstic 137412-107: Peter Packrat/Xybots/2p Gauntlet/720 (confirmed) */
	static slapstic_data slapstic107 = new slapstic_data
        (
		/* basic banking */
		3,								/* starting bank */
		new int[]{ 0x0018,0x001a,0x001c,0x001e },/* bank select values */
	
		/* alternate banking */
		new mask_value( 0x007f,0x006b ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x3d52 ),				/* 2nd mask/value in sequence */
		new mask_value( 0x3ffc,0x3d64 ),				/* 3rd mask/value in sequence */
		0,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( 0x3ff0,0x00a0 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3ff3,0x00a0 ),				/* clear bit 0 value */
		new mask_value( 0x3ff3,0x00a1 ),				/*   set bit 0 value */
		new mask_value( 0x3ff3,0x00a2 ),				/* clear bit 1 value */
		new mask_value( 0x3ff3,0x00a3 ),				/*   set bit 1 value */
		new mask_value( 0x3ff8,0x00b0 ),				/* final mask/value in sequence */
	
		/* additive banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN )
        );
	
	
	/* slapstic 137412-108: Road Runner/Super Sprint (confirmed) */
	static slapstic_data slapstic108 = new slapstic_data
        (
		/* basic banking */
		3,								/* starting bank */
		new int[]{ 0x0028,0x002a,0x002c,0x002e },/* bank select values */
	
		/* alternate banking */
		new mask_value( 0x007f,0x001f ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x3772 ),				/* 2nd mask/value in sequence */
		new mask_value( 0x3ffc,0x3764 ),				/* 3rd mask/value in sequence */
		0,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( 0x3ff0,0x0060 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3ff3,0x0060 ),				/* clear bit 0 value */
		new mask_value( 0x3ff3,0x0061 ),				/*   set bit 0 value */
		new mask_value( 0x3ff3,0x0062 ),				/* clear bit 1 value */
		new mask_value( 0x3ff3,0x0063 ),				/*   set bit 1 value */
		new mask_value( 0x3ff8,0x0070 ),				/* final mask/value in sequence */
	
		/* additive banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN )
        );
	
	
	/* slapstic 137412-109: Championship Sprint (confirmed) */
	static slapstic_data slapstic109 = new slapstic_data
        (
		/* basic banking */
		3,								/* starting bank */
		new int[]{ 0x0008,0x000a,0x000c,0x000e },/* bank select values */
	
		/* alternate banking */
		new mask_value( 0x007f,0x002b ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x0052 ),				/* 2nd mask/value in sequence */
		new mask_value( 0x3ffc,0x0064 ),				/* 3rd mask/value in sequence */
		0,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( 0x3ff0,0x3da0 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3ff3,0x3da0 ),				/* clear bit 0 value */
		new mask_value( 0x3ff3,0x3da1 ),				/*   set bit 0 value */
		new mask_value( 0x3ff3,0x3da2 ),				/* clear bit 1 value */
		new mask_value( 0x3ff3,0x3da3 ),				/*   set bit 1 value */
		new mask_value( 0x3ff8,0x3db0 ),				/* final mask/value in sequence */
	
		/* additive banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN )
        );
	
	
	/* slapstic 137412-110: Road Blasters/APB (confirmed) */
	static slapstic_data slapstic110 = new slapstic_data
        (
		/* basic banking */
		3,								/* starting bank */
		new int[]{ 0x0040,0x0050,0x0060,0x0070 },/* bank select values */
	
		/* alternate banking */
		new mask_value( 0x007f,0x002d ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x3d14 ),				/* 2nd mask/value in sequence */
		new mask_value( 0x3ffc,0x3d24 ),				/* 3rd mask/value in sequence */
		0,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( 0x3ff0,0x34c0 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3ff3,0x34c0 ),				/* clear bit 0 value */
		new mask_value( 0x3ff3,0x34c1 ),				/*   set bit 0 value */
		new mask_value( 0x3ff3,0x34c2 ),				/* clear bit 1 value */
		new mask_value( 0x3ff3,0x34c3 ),				/*   set bit 1 value */
		new mask_value( 0x3ff8,0x34d0 ),				/* final mask/value in sequence */
	
		/* additive banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN )
        );
	
	
	
	/*************************************
	 *
	 *	Slapstic-2 definitions
	 *
	 *************************************/
	
	/* slapstic 137412-111: Pit Fighter (confirmed) */
	static slapstic_data slapstic111 = new slapstic_data
        (
		/* basic banking */
		0,								/* starting bank */
		new int[]{ 0x0042,0x0052,0x0062,0x0072 },/* bank select values */
	
		/* alternate banking */
		new mask_value( 0x007f,0x000a ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x28a4 ),				/* 2nd mask/value in sequence */
		new mask_value( 0x0784,0x0080 ),				/* 3rd mask/value in sequence */
		0,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
	
		/* additive banking */
		new mask_value( 0x3fff,0x00a1 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x00a2 ),				/* 2nd mask/value in sequence */
		new mask_value( 0x3c5f,0x284d ),				/* +1 mask/value */
		new mask_value( 0x3e5f,0x2c5d ),				/* +2 mask/value */
		new mask_value( 0x3e5f,0x285d ),				/* +3 mask/value */
		new mask_value( 0x3ff8,0x2800 )				/* final mask/value in sequence */
        );
	
	
	/* slapstic 137412-116: Hydra (confirmed) */
	static slapstic_data slapstic116 = new slapstic_data
        (
		/* basic banking */
		0,								/* starting bank */
		new int[]{ 0x0044,0x004c,0x0054,0x005c },/* bank select values */
	
		/* alternate banking */
		new mask_value( 0x007f,0x0069 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x2bab ),				/* 2nd mask/value in sequence */
		new mask_value( 0x387c,0x0808 ),				/* 3rd mask/value in sequence */
		0,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
	
		/* additive banking */
		new mask_value( 0x3fff,0x3f7c ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x3f7d ),				/* 2nd mask/value in sequence */
		new mask_value( 0x3db2,0x3c12 ),				/* +1 mask/value */
		new mask_value( 0x3ff3,0x3e43 ),				/* +2 mask/value */
		new mask_value( 0x3ff3,0x3e53 ),				/* +3 mask/value */
		new mask_value( 0x3fff,0x2ba8 )				/* final mask/value in sequence */
        );
	
	
	/* slapstic 137412-117: Race Drivin' (confirmed) */
	static slapstic_data slapstic117 = new slapstic_data
	(
		/* basic banking */
		0,								/* starting bank */
		new int[]{ 0x0008,0x001a,0x002c,0x003e },/* bank select values */
	
		/* alternate banking */
		new mask_value( UNKNOWN,UNKNOWN ),			/* 1st mask/value in sequence */
		new mask_value( UNKNOWN,UNKNOWN ),			/* 2nd mask/value in sequence */
		new mask_value( UNKNOWN,UNKNOWN ),			/* 3rd mask/value in sequence */
		0,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
	
		/* additive banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN )				/* final mask/value in sequence */
        );
	
	
	/* slapstic 137412-118: Rampart/Vindicators II (confirmed) */
	static slapstic_data slapstic118 = new slapstic_data
        (
		/* basic banking */
		0,								/* starting bank */
		new int[]{ 0x0014,0x0034,0x0054,0x0074 },/* bank select values */
	
		/* alternate banking */
		new mask_value( 0x007f,0x0002 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x1950 ),				/* 2nd mask/value in sequence */
		new mask_value( 0x0067,0x0020 ),				/* 3rd mask/value in sequence */
		3,								/* shift to get bank from 3rd */
	
		/* bitwise banking */
		new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
                new mask_value( UNKNOWN,UNKNOWN ),
	
		/* additive banking */
		new mask_value( 0x3fff,0x1958 ),				/* 1st mask/value in sequence */
		new mask_value( 0x3fff,0x1959 ),				/* 2nd mask/value in sequence */
		new mask_value( 0x3f77,0x3056 ),				/* +1 mask/value */
		new mask_value( 0x3f77,0x3042 ),				/* +2 mask/value */
		new mask_value( 0x3f77,0x3052 ),				/* +3 mask/value */
		new mask_value( 0x3ff8,0x30e0 )				/* final mask/value in sequence */
        );
	
	
	
	/*************************************
	 *
	 *	Master slapstic table
	 *
	 *************************************/
	
	/* master table */
	static slapstic_data slapstic_table[] =
	{
		slapstic101,	/* NOT confirmed! */
		null,			/* never seen */
		slapstic103,
		slapstic104,
		slapstic105,
		slapstic106,	/* NOT confirmed! */
		slapstic107,
		slapstic108,
		slapstic109,
		slapstic110,
		slapstic111,
		null,			/* never seen */
		null,			/* never seen */
		null,			/* never seen */
		null,			/* never seen */
		slapstic116,
		slapstic117,
		slapstic118
	};
	
	
	
	/*************************************
	 *
	 *	Statics
	 *
	 *************************************/
	
	static int state;
	static int current_bank;
	static int access_68k;
	
	static int alt_bank;
	static int bit_bank;
	static int add_bank;
	static int bit_xor;
	
	static slapstic_data slapstic;
	
	
	/*#if LOG_SLAPSTIC
		static void slapstic_log(offs_t offset);
		static FILE *slapsticlog;
	#else
		#define slapstic_log(o)
	#endif*/
	
	
	
	/*************************************
	 *
	 *	Initialization
	 *
	 *************************************/
	
	public static void slapstic_init(int chip)
	{
		/* only a small number of chips are known to exist */
		if (chip < 101 || chip > 118)
			return;
	
		/* set up the parameters */
		if (slapstic_table[chip - 101] == null)
			return;
		slapstic = slapstic_table[chip - 101];
	
		/* reset the chip */
		slapstic_reset();
	
		/* see if we're 68k or 6502/6809 based */
		access_68k = (((Machine.drv.cpu[0].cpu_type & ~CPU_FLAGS_MASK) != CPU_M6809) &&
					  ((Machine.drv.cpu[0].cpu_type & ~CPU_FLAGS_MASK) != CPU_M6502))?1:0;
	}
	
	
	public static void slapstic_reset()
	{
		/* reset the chip */
		state = DISABLED;
	
		/* the 111 and later chips seem to reset to bank 0 */
		current_bank = slapstic.bankstart;
	}
	
	
	
	/*************************************
	 *
	 *	Returns active bank without tweaking
	 *
	 *************************************/
	
	public static int slapstic_bank()
	{
		return current_bank;
	}
	
	
	
	/*************************************
	 *
	 *	Kludge to catch alt seqeuences
	 *
	 *************************************/
	
	static int alt2_kludge(int offset)
	{
		int pc = cpu_getpreviouspc();
	
		/* 68k case is fairly complex: we need to look for special triplets */
		if (access_68k != 0)
		{
			/* first verify that the prefetched PC matches the first alternate */
			if ((((pc + 2) >> 1) & slapstic.alt1.mask) == slapstic.alt1.value)
			{
				/* now look for a move.w (An),(An) or cmpm.w (An)+,(An)+ */
				/*TODO*///int opcode = cpu_readop16(pc & 0xffffff);
                                int opcode = 0;
				if ((opcode & 0xf1f8) == 0x3090 || (opcode & 0xf1f8) == 0xb148)
				{
					/* fetch the value of the register for the second operand, and see */
					/* if it matches the third alternate */
					/*TODO*///int regval = cpu_get_reg(M68K_A0 + ((opcode >> 9) & 7)) >> 1;
                                        int regval = 0;
					if ((regval & slapstic.alt3.mask) == slapstic.alt3.value)
					{
						alt_bank = (regval >> slapstic.altshift) & 3;
						return ALTERNATE3;
					}
				}
				return ALTERNATE2;
			}
		}
	
		/* kludge for ESB */
		return ALTERNATE2;
	};
	
	
	
	/*************************************
	 *
	 *	Call this *after* every access
	 *
	 *************************************/
	
	public static int slapstic_tweak(int offset)
	{
		/* reset is universal */
		if (offset == 0x0000)
		{
			state = ENABLED;
		}
	
		/* otherwise, use the state machine */
		else
		{
			switch (state)
			{
				/* DISABLED state: everything is ignored except a reset */
				case DISABLED:
					break;
	
				/* ENABLED state: the chip has been activated and is ready for a bankswitch */
				case ENABLED:
	
					/* check for request to enter bitwise state */
					if ((offset & slapstic.bit1.mask) == slapstic.bit1.value)
					{
						state = BITWISE1;
					}
	
					/* check for request to enter additive state */
					else if ((offset & slapstic.add1.mask) == slapstic.add1.value)
					{
						state = ADDITIVE1;
					}
	
					/* check for request to enter alternate state */
					else if ((offset & slapstic.alt1.mask) == slapstic.alt1.value)
					{
						state = ALTERNATE1;
					}
	
					/* special kludge for catching the second alternate address if */
					/* the first one was missed (since it's usually an opcode fetch) */
					else if ((offset & slapstic.alt2.mask) == slapstic.alt2.value)
					{
						state = alt2_kludge(offset);
					}
	
					/* check for standard bankswitches */
					else if (offset == slapstic.bank[0])
					{
						state = DISABLED;
						current_bank = 0;
					}
					else if (offset == slapstic.bank[1])
					{
						state = DISABLED;
						current_bank = 1;
					}
					else if (offset == slapstic.bank[2])
					{
						state = DISABLED;
						current_bank = 2;
					}
					else if (offset == slapstic.bank[3])
					{
						state = DISABLED;
						current_bank = 3;
					}
					break;
	
				/* ALTERNATE1 state: look for alternate2 offset, or else fall back to ENABLED */
				case ALTERNATE1:
					if ((offset & slapstic.alt2.mask) == slapstic.alt2.value)
					{
						state = ALTERNATE2;
					}
					else
					{
						state = ENABLED;
					}
					break;
	
				/* ALTERNATE2 state: look for altbank offset, or else fall back to ENABLED */
				case ALTERNATE2:
					if ((offset & slapstic.alt3.mask) == slapstic.alt3.value)
					{
						state = ALTERNATE3;
						alt_bank = (offset >> slapstic.altshift) & 3;
					}
					else
					{
						state = ENABLED;
					}
					break;
	
				/* ALTERNATE3 state: wait for a standard bank value to finish the transaction */
				case ALTERNATE3:
					if (offset == slapstic.bank[0] || offset == slapstic.bank[1] ||
						offset == slapstic.bank[2] || offset == slapstic.bank[3])
					{
						state = DISABLED;
						current_bank = alt_bank;
					}
					break;
	
				/* BITWISE1 state: waiting for a bank to enter the BITWISE state */
				case BITWISE1:
					if (offset == slapstic.bank[0] || offset == slapstic.bank[1] ||
						offset == slapstic.bank[2] || offset == slapstic.bank[3])
					{
						state = BITWISE2;
						bit_bank = current_bank;
						bit_xor = 0;
					}
					break;
	
				/* BITWISE2 state: watch for twiddling and the escape mechanism */
				case BITWISE2:
	
					/* check for clear bit 0 case */
					if (((offset ^ bit_xor) & slapstic.bit2c0.mask) == slapstic.bit2c0.value)
					{
						bit_bank &= ~1;
						bit_xor ^= 3;
					}
	
					/* check for set bit 0 case */
					else if (((offset ^ bit_xor) & slapstic.bit2s0.mask) == slapstic.bit2s0.value)
					{
						bit_bank |= 1;
						bit_xor ^= 3;
					}
	
					/* check for clear bit 1 case */
					else if (((offset ^ bit_xor) & slapstic.bit2c1.mask) == slapstic.bit2c1.value)
					{
						bit_bank &= ~2;
						bit_xor ^= 3;
					}
	
					/* check for set bit 1 case */
					else if (((offset ^ bit_xor) & slapstic.bit2s1.mask) == slapstic.bit2s1.value)
					{
						bit_bank |= 2;
						bit_xor ^= 3;
					}
	
					/* check for escape case */
					else if ((offset & slapstic.bit3.mask) == slapstic.bit3.value)
					{
						state = BITWISE3;
					}
					break;
	
				/* BITWISE3 state: waiting for a bank to seal the deal */
				case BITWISE3:
					if (offset == slapstic.bank[0] || offset == slapstic.bank[1] ||
						offset == slapstic.bank[2] || offset == slapstic.bank[3])
					{
						state = DISABLED;
						current_bank = bit_bank;
					}
					break;
	
				/* ADDITIVE1 state: look for add2 offset, or else fall back to ENABLED */
				case ADDITIVE1:
					if ((offset & slapstic.add2.mask) == slapstic.add2.value)
					{
						state = ADDITIVE2;
						add_bank = current_bank;
					}
					else
					{
						state = ENABLED;
					}
					break;
	
				/* ADDITIVE2 state: watch for twiddling and the escape mechanism */
				case ADDITIVE2:
	
					/* check for clear bit 0 case */
					if ((offset & slapstic.addplus1.mask) == slapstic.addplus1.value)
					{
						add_bank = (add_bank + 1) & 3;
					}
	
					/* check for set bit 0 case */
					else if ((offset & slapstic.addplus2.mask) == slapstic.addplus2.value)
					{
						add_bank = (add_bank + 2) & 3;
					}
	
					/* check for clear bit 0 case */
					else if ((offset & slapstic.addplus3.mask) == slapstic.addplus3.value)
					{
						add_bank = (add_bank + 3) & 3;
					}
	
					/* check for escape case */
					else if ((offset & slapstic.add3.mask) == slapstic.add3.value)
					{
						state = ADDITIVE3;
					}
					break;
	
				/* ADDITIVE3 state: waiting for a bank to seal the deal */
				case ADDITIVE3:
					if (offset == slapstic.bank[0] || offset == slapstic.bank[1] ||
						offset == slapstic.bank[2] || offset == slapstic.bank[3])
					{
						state = DISABLED;
						current_bank = add_bank;
					}
					break;
			}
		}
	
		/* log this access */
		/*TODO*///slapstic_log(offset);
	
		/* return the active bank */
		return current_bank;
	}
	
	
	
	/*************************************
	 *
	 *	Debugging
	 *
	 *************************************/
	
	/*#if LOG_SLAPSTIC
	static void slapstic_log(int offset)
	{
		static double last_time;
	
		if (slapsticlog == 0)
			slapsticlog = fopen("slapstic.log", "w");
		if (slapsticlog)
		{
			double time = timer_get_time();
	
			if (time - last_time > 1.0)
				fprintf(slapsticlog, "------------------------------------\n");
			last_time = time;
	
			fprintf(slapsticlog, "%06X: %04X B=%d ", cpu_getpreviouspc(), offset, current_bank);
			switch (state)
			{
				case DISABLED:
					fprintf(slapsticlog, "DISABLED\n");
					break;
				case ENABLED:
					fprintf(slapsticlog, "ENABLED\n");
					break;
				case ALTERNATE1:
					fprintf(slapsticlog, "ALTERNATE1\n");
					break;
				case ALTERNATE2:
					fprintf(slapsticlog, "ALTERNATE2\n");
					break;
				case ALTERNATE3:
					fprintf(slapsticlog, "ALTERNATE3\n");
					break;
				case BITWISE1:
					fprintf(slapsticlog, "BITWISE1\n");
					break;
				case BITWISE2:
					fprintf(slapsticlog, "BITWISE2\n");
					break;
				case BITWISE3:
					fprintf(slapsticlog, "BITWISE3\n");
					break;
				case ADDITIVE1:
					fprintf(slapsticlog, "ADDITIVE1\n");
					break;
				case ADDITIVE2:
					fprintf(slapsticlog, "ADDITIVE2\n");
					break;
				case ADDITIVE3:
					fprintf(slapsticlog, "ADDITIVE3\n");
					break;
			}
			fflush(slapsticlog);
		}
	}
	#endif
        */
}
