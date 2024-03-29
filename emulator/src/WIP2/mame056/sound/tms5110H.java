/**
 * ported to 0.58
 * ported to v0.37b7
 * 
 */
/**
 * Changelog
 * ---------
 * 26/04/2019 - ported 5110 sound  to 0.58 (shadow)
 */
package WIP2.mame056.sound;

public class tms5110H {

    /* TMS5110 commands */
 /* CTL8  CTL4  CTL2  CTL1  |   PDC's  */
 /* (MSB)             (LSB) | required */
    public static final int TMS5110_CMD_RESET = (0);
    /*    0     0     0     x  |     1    */
    public static final int TMS5110_CMD_LOAD_ADDRESS = (2);
    /*    0     0     1     x  |     2    */
    public static final int TMS5110_CMD_OUTPUT = (4);
    /*    0     1     0     x  |     3    */
    public static final int TMS5110_CMD_READ_BIT = (8);
    /*    1     0     0     x  |     1    */
    public static final int TMS5110_CMD_SPEAK = (10);
    /*    1     0     1     x  |     1    */
    public static final int TMS5110_CMD_READ_BRANCH = (12);
    /*    1     1     0     x  |     1    */
    public static final int TMS5110_CMD_TEST_TALK = (14);

    /*    1     1     1     x  |     3    */

    public static abstract interface M0_callbackPtr {

        public abstract int handler();
    }
}
