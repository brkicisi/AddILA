/*
 * This is a class to assist with debugging Vivado projects.
 * 
 * It uses RapidWright and Vivado tcl commands to try to insert an ila into a given
 * routed design.
 * It also inserts probes connecting them to the ila either from a specified probes file
 * or from nets marked for debug.
 * 
 * 
 * Some assumptions made:
 *      - provided design is from <proj_dir>/<proj_name>.runs/impl_1/<design_name>_wrapper_*.dcp
 *      - ila was inserted using this script (if continuing from intermediate dcp)
 *      - Vivado can be called from command line using 'vivado'
 *      - class path given to java includes RapidWright paths
 *      
 * Notes:
 *      - some changes made to RapidWright code were made (described in ILAInserter.txt)
 * 
 * 
 * 
 * Start: May 2019
 * Original author: Isidor (Igi) Brkic
 * Vivado version: 18.3
 * RapidWright version: 2018.3.3 (installed 17 May 2018)
 * 
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Comparator;
import java.lang.String;
import java.lang.StringBuilder;

import java.lang.ArrayIndexOutOfBoundsException;
import java.lang.SecurityException;

import com.xilinx.rapidwright.util.MessageGenerator;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.StringTools;
import com.xilinx.rapidwright.debug.*;
import com.xilinx.rapidwright.design.*;
import com.xilinx.rapidwright.edif.*;
import com.xilinx.rapidwright.router.Router;
import com.xilinx.rapidwright.device.BELPin;
import com.xilinx.rapidwright.design.blocks.PBlock;


public class ILADebug {
    /**
     * stores info relating to a positional argument
     */
    static class MyPositionalArg {
        String name;
        String help_str;
        boolean required;

        public MyPositionalArg(String name, boolean required){
            this(name, required, null);
        }
        public MyPositionalArg(String name, boolean required, String help_str){
            this.name = name;
            this.required = required;
            this.help_str = help_str;
        }
        @Override
        public String toString(){
            if(required)
                return "<" + name + ">";
            return "[<" + name + ">]";
        }
        public String getHelp(){
            return (help_str == null) ? "" : help_str;
        }
    }
    /**
     * stores info relating to a recognized token
     */
    static class MyToken {
        String name;        // token name
        String help_str;
        String[] options;   // str recognized to invoke the token
        MyPositionalArg[] args;
        
        public MyToken(String name, String[] options){
            this(name, options, null, null, null);
        }
        public MyToken(String name, String[] options, String help_str){
            this(name, options, null, null, help_str);
        }
        public MyToken(String name, String[] options, String[] arg_names, boolean[] required, String help_str){
            this.name = (name == null) ? "default" : name;
            this.help_str = help_str;
            this.options = (options == null) ? new String[]{} : options;
            // length of args is equal to shorter of arg_names and required
            int arg_len = 0;
            try{
                arg_len = arg_names.length < required.length ? arg_names.length : required.length;
            } catch(NullPointerException npe){}

            if(arg_len <= 0)
                args = new MyPositionalArg[]{};
            else {
                args = new MyPositionalArg[arg_len];
                for(int i = 0 ; i < args.length ; i++)
                    args[i] = new MyPositionalArg(arg_names[i], required[i]);
            }
        }
        @Override
        public String toString(){
            StringBuilder sb = new StringBuilder("[");
            for(int i = 0 ; i < options.length ; i++){
                if(i != 0)
                    sb.append("|");
                sb.append(options[i]);
            }
            if(args.length > 0)
                sb.append(" ");
            for(int i = 0 ; i < args.length ; i++)
                sb.append(args[i].toString());
            sb.append("]");
            return sb.toString();
        }
        public String shortString(){
            StringBuilder sb = new StringBuilder("[");
            sb.append(options[0]);
            if(args.length > 0)
                sb.append(" ");
            for(int i = 0 ; i < args.length ; i++)
                sb.append(args[i].toString());
            sb.append("]");
            return sb.toString();
        }
        public String getHelp(){
            return (help_str == null) ? "" : help_str;
        }
    }
    
    // This comparator was taken directly from com/xilinx/rapidwright/util/StringTools.java
    private static Comparator<String> naturalComparator;
	static {
		naturalComparator = new Comparator<String>() {
			private boolean isDigit(char c){
				return 0x30 <= c && c <= 0x39;
			}
			
			@Override
			public int compare(String a, String b){
				int ai = 0, bi = 0;
				while(ai < a.length() && bi < b.length()){
					if(isDigit(a.charAt(ai)) && isDigit(b.charAt(bi))){
						int aStart = ai, bStart = bi;
						while(ai < a.length() && isDigit(a.charAt(ai))) ai++;
						while(bi < b.length() && isDigit(b.charAt(bi))) bi++;
						int aInt = Integer.parseInt(a.substring(aStart,ai));
						int bInt = Integer.parseInt(b.substring(bStart,bi));
						if(aInt != bInt) return aInt - bInt;
					} else if(a.charAt(ai) != b.charAt(bi)) 
						return a.charAt(ai) - b.charAt(bi);
					ai++; bi++;
				}
				return a.length() - b.length();
			}
		};
    }
    
    static enum TCLEnum {
        SOURCE_RW("source " + FileTools.getRapidWrightPath() + File.separator + FileTools.TCL_FOLDER_NAME
                        + File.separator + "rapidwright.tcl", "q", null),
        OPEN_DCP("open_checkpoint", "q", ".dcp"),
        WRITE_DCP("write_checkpoint", "qf", ".dcp"),
        PLACE("place_design", "q", null),
        ROUTE("route_design", "q", null),
        WRITE_EDIF("write_edif", "qf", ".edf"),
        WRITE_LTX("write_debug_probes", "qf", ".ltx"),
        WRITE_BITSTREAM("write_bitstream", "qf", ".bit");

        private final String command;
        private final String options;
        private final String extension;
        private final String force = "-force";
        private final String quiet = "-quiet";

        TCLEnum(String command, String options, String extension){
            this.command = command;
            this.options = options;
            this.extension = extension;
        }
        String ext(){return extension;}
        String cmd(){return command;}
        String cmd(String opts){
            StringBuffer sb = new StringBuffer(command);
            if(opts != null) {
                for(int i = 0 ; i < opts.length() ; i++){
                    if(options.indexOf(opts.charAt(i)) == -1)
                        continue;
                    switch(opts.charAt(i)){
                        case 'f':
                            sb.append(" " + force);
                            break;
                        case 'q':
                            sb.append(" " + quiet);
                            break;
                        default:
                            break;
                    }
                }
            }
            return sb.toString();
        }
    }

    static class TCLCommand {
        TCLEnum tcl_cmd;
        String options;
        String filename;
        
        TCLCommand(TCLEnum tcl_cmd, String filename){
            this(tcl_cmd, null, filename);
        }
        TCLCommand(TCLEnum tcl_cmd, String options, String filename){
            this.tcl_cmd = tcl_cmd;
            this.options = options;
            if(tcl_cmd.ext() != null)
                this.filename = FileTools.removeFileExtension(filename) + tcl_cmd.ext();
            else
                this.filename = null;
        }
        @Override
        public String toString(){
            if(filename == null || tcl_cmd.ext() == null)
                return tcl_cmd.cmd(options);
            return tcl_cmd.cmd(options) + " " + filename;
        }
    }

    class TCLScript {
        List<TCLCommand> tcl_script = null;
        private static final String run_vivado = "vivado -mode batch -log vivado.log -journal vivado.jou -source";
        String tcl_script_name = null;
        String output_file = null;
        String options = null;
        File tcl_file = null;
        
        TCLScript(String input_dcp, String output_file, String tcl_script_name){
            this(null, input_dcp, output_file, (quiet() ? "q" : "") + (force() ? "f" : ""), tcl_script_name);
        }

        /**
         * Same, but options are taken from those input at command line.
         */
        TCLScript(List<TCLEnum> cmds, String input_dcp, String output_file, String tcl_script_name){
            this(cmds, input_dcp, output_file, (quiet() ? "q" : "") + (force() ? "f" : ""), tcl_script_name);
        }
        
        /**
         * Construct a tcl script object. First sources RapidWright. Then opens checkpoint. Then executes cmds.
         * @param cmds TCL commands to execute after opening checkpoint.
         * @param input_dcp Design checkpoint file to open.
         * @param output_file File to store to. Extension is automatically changed based on command (directory is not changed).
         * @param options String of options to execute commands with. Currently only "f", "q", and combinations are supported.
         * @param tcl_script_name File in which to save the script relative to .iii directory
         */
        TCLScript(List<TCLEnum> cmds, String input_dcp, String output_file, String options, 
                    String tcl_script_name){
            this.tcl_script_name = tcl_script_name;
            this.output_file = output_file;
            this.options = options;

            tcl_script = new ArrayList<>();
            tcl_script.add(new TCLCommand(TCLEnum.SOURCE_RW, "q", null));
            tcl_script.add(new TCLCommand(TCLEnum.OPEN_DCP, options, input_dcp));
            
            if(cmds != null)
                for(TCLEnum te : cmds)
                    tcl_script.add(new TCLCommand(te, options, output_file));
        }

        void add(TCLEnum te){
            tcl_script.add(new TCLCommand(te, options, output_file));
        }
        void add(TCLEnum te, String opts){
            tcl_script.add(new TCLCommand(te, opts, output_file));
        }

        /**
         * Write tcl script to file.
         * @return Success.
         */
        boolean write(){
            if(iii_dir == null)
                return false;

            tcl_file = new File(iii_dir, tcl_script_name);
            List<String> tcl_strs = new ArrayList<>();
            for(TCLCommand cmd : tcl_script)
                tcl_strs.add(cmd.toString());
            FileTools.writeLinesToTextFile(tcl_strs, tcl_file.getAbsolutePath());
            return tcl_file.exists();
        }
        /**
         * Execute tcl script.
         * @return Success.
         */
        boolean run(){
            boolean wrote = write();
            if(!wrote)
                return false;
            
            FileTools.runCommand(run_vivado + " " + tcl_file.getAbsolutePath(), true);
            return true;
        }
    }


    // Class ILADebug variables

    HashMap<String, MyToken> token_map;
    HashMap<String, ArrayList<String>> arg_map;

    File pwd_dir = null;
    File iii_dir = null;
    File no_ila_dcp_file = null;
    File no_probes_dcp_file = null;
    File output_dcp_file = null;
    File input_probes_file = null;
    File output_probes_file = null;

    Design design = null;
    Map<String, String> probe_map = null;
    int probe_count = 0;
    int probe_depth = 4096;
    String clk_net = null;

    String default_net = null;
    Map<String, String> meta_map = null;


    private static final int MAX_PROBE_COUNT = 4096;

    // constructor
    public ILADebug(){
        arg_map = new HashMap<>();
        token_map = new HashMap<>();
        for(MyToken t : TOKEN_LIST)
            for(String op : t.options)
                token_map.put(op, t);
    }

    /**
     * True if force was part of the command line args.
     * @return
     */
    public boolean force(){
        return arg_map.containsKey("force");
    }
    /**
     * True if verbose was part of the command line args and quiet wasn't.
     * @return
     */
    public boolean verbose(){
        return !quiet() && arg_map.containsKey("verbose");
    }
    /**
     * True if quiet was part of the command line args.
     * @return
     */
    public boolean quiet(){
        return arg_map.containsKey("quiet");
    }
    /**
     * Prints the string if verbose was part of the command line args.
     * @param s String to be printed.
     */
    private void printIfVerbose(String s){
        if(verbose())
            MessageGenerator.briefMessage(s);
    }

    private static String helpLine(String arg_str, String help_str, int longest){
        int padding = longest - arg_str.length() + 2;
        String pad_str = String.format("%1$" + padding + "s", " - ");
        return arg_str + pad_str + help_str + "\n";
    }
    /**
     * Print help information and exit.
     * Shows all recognized arguments both optional and positional.
     */
    private static void printHelp() {
        int longest = 0; // needed to pad so output looks nicer.
        StringBuilder sb = new StringBuilder("USAGE: ");
        for(MyToken a : TOKEN_LIST){
            sb.append(a.shortString());
            if(a.toString().length() > longest)
                longest = a.toString().length();
        }
        for(MyPositionalArg a : POSITIONAL_ARGS){
            sb.append(" " + a);
            if(a.toString().length() > longest)
                longest = a.toString().length();
        }
        sb.append("\n\n");
        for(MyToken a : TOKEN_LIST)
            sb.append(helpLine(a.toString(), a.getHelp(), longest));
        for(MyPositionalArg a : POSITIONAL_ARGS)
            sb.append(helpLine(a.toString(), a.getHelp(), longest));
        sb.append("\n");
        MessageGenerator.briefMessageAndExit(sb.toString());
    }
    // used {c, d, f, i, o, Pp, q, r, v}
    private static final MyToken[] TOKEN_LIST = {
        new MyToken("input_probes_file", new String[]{"-i", "--input_probes"}, 
                new String[]{"probes_txt"}, new boolean[]{true},
                "Insert probes using mappings from this file."),
        new MyToken("output_probes_file", new String[]{"-o", "--output_probes"}, 
                new String[]{"probes_txt"}, new boolean[]{true},
                "Write probe mappings to this file."),
        new MyToken("iii_dir", new String[]{"-d", "--iii_dir"}, 
                new String[]{"iii_dir"}, new boolean[]{true},
                "Location of .iii directory. Intermediate designs and other temporary files are stored here."),
        new MyToken("probe_count", new String[]{"-p", "--probe_count"},
                new String[]{"probe_count"}, new boolean[]{true},
                "Insert this many probe wires (ignored if smaller than number of wires in probe map). "
                + "May force refresh if input number of wires differs "
                + "from the number of wires in the intermediate design. Default: number of net wires "
                + "in probe map (from probes file or nets marked for debug)."),
        new MyToken("probe_depth", new String[]{"-P", "--probe_depth"},
                new String[]{"probe_depth"}, new boolean[]{true},
                "Use this depth for probes. May force refresh if input number of wires differs "
                + "from the depth of probes in the intermediate design. Default: '4096'."),
        new MyToken("clk_net", new String[]{"-c", "--probe_count"},
                new String[]{"clk_net"}, new boolean[]{true},
                "The net connecting to the clock. Default: 'clk_100MHz'."),
        new MyToken("refresh", new String[]{"-r", "--refresh"},
                "Force recompilation from input dcp. Ignore any intermediate designs."),
        new MyToken("force", new String[]{"-f", "--force"},
                "Force overwrite of output files (intermediate files in .iii are always overwritten)."),
        new MyToken("quiet", new String[]{"-q", "--quiet"},
                "Display less output."),
        new MyToken("verbose", new String[]{"-v", "--verbose"},
                "Display extra progress information (ignored if also quiet).")
    };
    private static final MyPositionalArg[] POSITIONAL_ARGS = {
        new MyPositionalArg("input_dcp", true,
                "Original design checkpoint file generated by Vivado in '<project_name>.runs/impl_1/<design_name>_wrapper_routed.dcp'."),
        new MyPositionalArg("output_dcp", true,
                "Name to give final output design.")
    };
    private static final String[] HELP_SWITCH = {"-h", "--help"};

    /**
     * Takes in a command line list and parses it into a HashMap.
     * @param args command line list
     */
    private void mapArgs(String[] args){
        // if any argument is in the set of help switches, print help and exit
        for(String a : args)
            for(String sw : HELP_SWITCH)
                if(a.equalsIgnoreCase(sw))
                    printHelp();

        int positional_arg_counter = 0;
        for(int i = 0 ; i < args.length ; i++){
            // token
            if(args[i].startsWith("-") || args[i].startsWith("--")){
                MyToken t = token_map.get(args[i]);
                if(t == null)
                    MessageGenerator.briefErrorAndExit("Unrecognized token at position " + i + ".\n");
                
                ArrayList<String> a_list = new ArrayList<>();
                for(int j = 0 ; j < t.args.length ; j++){
                    String a = null;
                    try {
                        a = args[i+j+1];
                    } catch(ArrayIndexOutOfBoundsException iobe){
                        if(t.args[j].required)
                            MessageGenerator.briefErrorAndExit(
                                "Not enough arguments for '" + args[i] + "' at position " + i + ".\n"
                                + t.toString() + "\n"
                            );
                        else
                            break;
                    }
                    if(a.startsWith("-") || a.startsWith("--")){
                        if(t.args[j].required)
                            MessageGenerator.briefErrorAndExit(
                                "Not enough arguments for '" + args[i] + "' at position " + i + ".\n"
                                + t.toString() + "\n"
                            );
                        else
                            break;
                    }
                    a_list.add(a);
                    i++;
                }
                arg_map.put(t.name, a_list);
            }
            // standard argument
            else {
                if(positional_arg_counter >= POSITIONAL_ARGS.length)
                    continue;
                
                ArrayList<String> a_list = new ArrayList<>();
                a_list.add(args[i]);
                arg_map.put(POSITIONAL_ARGS[positional_arg_counter].name, a_list);
                positional_arg_counter++;
            }
        }
        if(positional_arg_counter < POSITIONAL_ARGS.length 
                && POSITIONAL_ARGS[positional_arg_counter].required)
            MessageGenerator.briefErrorAndExit("Not enough positional arguments.\n");
    }

    /**
     * Writes the probe map to a file. The probe map is pairs of probe nets and the nets
     * in the design that they are connected to.
     * @param filename File to write probemap to.
     */
    public void writeProbesFile(String filename){
        List<String> p = new ArrayList<>();
        p.add("# Probe mapping: <full probe net path> -> <full debug net path>");
        p.add("# probe_count of design that wrote this = " + probe_count);

        List<String> probe_list = StringTools.naturalSort(new ArrayList<>(probe_map.keySet()));
        for (String probe : probe_list)
            p.add(probe + " " + probe_map.get(probe));
        FileTools.writeLinesToTextFile(p, filename);
    }

    /**
     * Writes probe_depth and clk_net to a file so that it knows what they were when it resumes from an
     * intermediate design.
     */
    public void writeMetadata(){
        File f = new File(iii_dir, "metadata.txt");
        printIfVerbose("Writing metadata to '" + f.getAbsolutePath() + "'");

        List<String> lines = new ArrayList<>();
        lines.add("# This is a metadata file.");
        lines.add("# Mappings: <key> -> <value>");
        
        lines.add("probe_depth" + " = " + probe_depth);
        lines.add("clk_net" + " = " + clk_net);
        
        FileTools.writeLinesToTextFile(lines, f.getAbsolutePath());
    }

    /**
     * Reads probe_depth and clk_net from a file. Used when loading an intermediate design.
     */
    public void readMetadata(){
        File f = new File(iii_dir, "metadata.txt");
        if(!f.exists()){
            printIfVerbose("No metadata found at '" + f.getAbsolutePath() + "'");
            return;
        }
        
        printIfVerbose("Reading metadata from '" + f.getAbsolutePath() + "'");

        meta_map = new HashMap<>();
        List<String> lines = FileTools.getLinesFromTextFile(f.getAbsolutePath());
		for(String line : lines){
            if(line.trim().startsWith("#"))
                continue;
            if(line.trim().isEmpty())
                continue;
			String[] parts = line.split("=");
			meta_map.put(parts[0].trim(), parts[1].trim());
		}
    }

    /**
     * Load probe_depth from command line or metadata or default (in that order).
     * @return True if command line arg contradicted metadata. False otherwise.
     */
    private boolean loadProbeDepth(){
        String key = "probe_depth";
        List<String> list = arg_map.get(key);
        String sv = null;
        int v = -1;

        try {
            if(meta_map != null && (sv = meta_map.get(key)) != null)
                v = Integer.parseInt(sv);
        } catch(NumberFormatException nfe){
            sv = null;
        }

        if(list != null){
            try {
                probe_depth = Integer.parseInt(list.get(0));
            } catch(NumberFormatException nfe){
                printIfVerbose("Couldn't parse '" + list.get(0) + "' as an integer " + key + ".");
                list = null;
            }

            if(sv != null && probe_depth != v){
                printIfVerbose(key + " '" + probe_depth + "' at command line did not equal "
                        + key + " '" + v + "' from metadata.");
                return true;
            }
            printIfVerbose("Used " + key + " of '" + probe_depth + "'.");
        }
        else if(sv != null || list == null){
            probe_depth = v;
            printIfVerbose("Used " + key + " of '" + probe_depth + "' from metadata.");
        }
        else {
            probe_depth = 4096;
            printIfVerbose("Used default " + key + " of '" + probe_depth + "'.");
        }
        return false;
    }

    /**
     * Load clk_net from command line or metadata or default (in that order).
     * @return True if command line arg contradicted metadata. False otherwise.
     */
    private boolean loadClkNet(){
        String key = "clk_net";
        List<String> list = arg_map.get(key);
        String v = null;
        if(meta_map != null){
            v = meta_map.get(key);
            if(v != null && v.equals("null"))
                v = null;
        }
        
        if(list != null){
            clk_net = list.get(0);
            if(v != null && !clk_net.equals(v)){
                printIfVerbose(key + " '" + clk_net + "' at command line did not equal "
                        + key + " '" + v + "' from metadata.");
                return true;
            }
            printIfVerbose("Used " + key + " of '" + clk_net + "'.");
        }
        else if(v != null){
            clk_net = meta_map.get(key);
            printIfVerbose("Used " + key + " of '" + clk_net + "' from metadata.");
        }
        else {
            clk_net = "clk_100MHz";
            printIfVerbose("Used default " + key + " of '" + clk_net + "'.");
        }
        return false;
    }

    /**
     * Runs write_edif tcl command using Vivado on the given design. Should write an
     * unencrypted edif file to the same directory as the given dcp file.
     * @param dcp_file Design checkpoint to write edif of.
     */
    public void generateEdif(String dcp_file){
        TCLScript script = new TCLScript(dcp_file, dcp_file, "generate_edif.tcl");
        script.add(TCLEnum.WRITE_EDIF);
        script.run();
    }

    public Design safeReadCheckpoint(File f_dcp){
        return safeReadCheckpoint(f_dcp.getAbsolutePath());
    }
    /**
     * Reads a design checkpoint file.
     * Catches the case where the edif file is encrypted and tries to generate the required
     * edif file using generateEdif().
     * @param dcp_file Design checkpoint to open.
     * @return A Design object.
     */
    public Design safeReadCheckpoint(String dcp_file){
        Design d = null;
        try {
            d = Design.readCheckpoint(dcp_file);
        } catch(RuntimeException e){
            printIfVerbose("\nCouldn't open design at '" + dcp_file + "' due to encrypted edif.");
            printIfVerbose("Trying to generate unencrypted edif using vivado.\n");
            generateEdif(dcp_file);
            printIfVerbose("Trying to open design again.\n");
            d = Design.readCheckpoint(dcp_file);
        }
        return d;
    }

    /**
     * Modify dcp/dsgn.edf because it won't open in Vivado without this change.
     * This change was determined expirementally and is not guaranteed to be the best way to solve this issue.
     * @param input_dcp Absolute path to dcp file containing edif file to be changed.
     * @param output_dcp Absolute path to write dcp file containing changed edif.
     * @return Completed successfully.
     */
    public boolean fixEdifInDCP(String input_dcp, String output_dcp){
        ZipInputStream zis = null;
        ZipOutputStream zos = null;
        byte[] buffer = new byte[1024];
        int len;

        printIfVerbose("\nStarting fix edif process.");
        try {
            zis = new ZipInputStream(new FileInputStream(input_dcp));
            zos = new ZipOutputStream(new FileOutputStream(output_dcp));

            ZipEntry ze = null;
            while((ze = zis.getNextEntry()) != null){
                if(ze.getName().endsWith(".edf")){
                    // fix edif and copy
                    zos.putNextEntry(ze);
                    StringBuffer sb = new StringBuffer();
                    while ((len = zis.read(buffer)) > 0)
                        sb.append(new String(buffer, 0, len));
                    String edif_str = sb.toString();

                    String bad_string = "(instance top (viewref design_1_wrapper (cellref design_1_wrapper (libraryref work)))";
                    String good_string = "(instance top (viewref netlist (cellref design_1_wrapper (libraryref work)))";
                    int index = edif_str.lastIndexOf(bad_string);
                    byte[] buffer2;
                    if(index >= 0){ // found bad_string
                        printIfVerbose("Replacing bad_string.");
                        String fixed_str = edif_str.substring(0, index) + good_string + edif_str.substring(index + bad_string.length());
                        buffer2 = fixed_str.getBytes();
                    }
                    else {
                        printIfVerbose("Warning: didn't find bad_string to replace. No change made to edif file.");
                        printIfVerbose("bad_string = '" + bad_string + "'");
                        buffer2 = edif_str.getBytes();
                    }
                    
                    zos.write(buffer2, 0, buffer2.length);
                    zos.closeEntry();
                }
                else {
                    // copy
                    zos.putNextEntry(ze);
                    while ((len = zis.read(buffer)) > 0)
                        zos.write(buffer, 0, len);
                    zos.closeEntry();
                }
                zis.closeEntry();
            }
            zis.close();
            zos.close();

            printIfVerbose("Deleting old dcp file.\n");
            FileTools.deleteFile(input_dcp);

        } catch(IOException e) {
            e.printStackTrace();
            if(zis != null)
                try { zis.close(); } catch(IOException ioe){}
            if(zos != null)
                try { zos.close(); } catch(IOException ioe){}
            return false;
        }
        return true;
    }

    public File getExistingFile(String filename, boolean error_if_not_found){
        return getFile(filename, false, false, null, error_if_not_found);
    }
    public File getFile(String filename, boolean create_in_pwd, boolean create_in_iii,
            String create_name, boolean error_if_not_found){
        return getFileOrDir(filename, false, create_in_pwd, create_in_iii, create_name, error_if_not_found);
    }
    public File getDir(String filename, boolean create_in_pwd, boolean create_in_iii,
            String create_name, boolean error_if_not_found){
        return getFileOrDir(filename, true, create_in_pwd, create_in_iii, create_name, error_if_not_found);
    }
    /**
     * Searches for filename. If neither create is true then not finding the file is an error
     * @param filename file/dir to search for
     * @param dir true if searching for directory
     * @param create_in_pwd if can't find, create file if true
     * @param create_in_iii if can't find, create file if true
     * @param create_name path/name to use to create file relative to pwd or .iii
     * @param error_if_not_found exit if can't find file
     * @return file that was found or created.
     */
    public File getFileOrDir(String filename, boolean dir, boolean create_in_pwd, 
            boolean create_in_iii, String create_name, boolean error_if_not_found){

        File f = null;

        // try to find file
        if(filename != null){
            // filename starts with '/' or '~', assume it is a complete path.
            if(filename.startsWith("/") || filename.startsWith("~"))
                f = new File(filename);
            else // assume filename relative to pwd
                f = new File(pwd_dir.getAbsolutePath() + "/" + filename);

            if(dir ? f.isDirectory() : f.exists())
                return f;

            if(error_if_not_found)
                MessageGenerator.briefErrorAndExit("Could not access/find "
                        + (dir ? "directory" : "file") + " '" + f.getPath() + "'.\n");
            
            printIfVerbose("Could not access/find "
                    + (dir ? "directory" : "file") + " '" + f.getPath() + "'.");
        }
        // else // filename == null
        //     printIfVerbose("Filename was null.");
        
        if(!create_in_iii && !create_in_pwd)
            return null;

        if(create_name == null){
            // printIfVerbose("Create_name was null.");
            return null;
        }

        // create file
        if(create_name.startsWith("/") || create_name.startsWith("~"))
            f = new File(create_name);
        else if(create_in_iii)
            f = new File(iii_dir.getAbsolutePath() + "/" + create_name);
        else if(create_in_pwd)
            f = new File(pwd_dir.getAbsolutePath() + "/" + create_name);

        if(dir){
            if(!f.isDirectory()){
                printIfVerbose("Creating new directory '" + f.getAbsolutePath() + "'.");
                if(!f.mkdirs())
                    MessageGenerator.briefErrorAndExit("Failed to create directroy '"
                            + f.getPath() + "'.\n");
            }
            return f;
        }
        if(!f.exists()){
            try {
                printIfVerbose("Creating new file '" + f.getAbsolutePath() + "'.");
                f.createNewFile();
            } catch(IOException ioe){
                System.err.println("IOException in : " + ioe.getMessage());
                ioe.printStackTrace();
                f = null;
            }
        }
        return f;
    }

    /**
     * Takes filenames from HashMap and determines if input files exist.
     */
    private void setFiles(){
        // note doesn't end in '/'
        String pwd_str = System.getProperty("user.dir");
        if(pwd_str == null)
           MessageGenerator.briefErrorAndExit("Could not access pwd.\n");
        
        pwd_dir = new File(pwd_str);
        if(pwd_dir == null || !pwd_dir.isDirectory())
            MessageGenerator.briefErrorAndExit("Could not access/find pwd '" 
                    + System.getProperty("user.dir") + "'.\n");

        ArrayList<String> files = arg_map.get("iii_dir");
        String filename = (files == null) ? null : files.get(0);
        String create_dir = (files == null || !filename.endsWith("/.iii")) ? ".iii" : filename;
        iii_dir = getDir(filename, true, false, create_dir, false);


        // input files
        
        // initial routed design without ila
        files = arg_map.get("input_dcp");
        filename = (files == null) ? null : files.get(0);
        no_ila_dcp_file = getExistingFile(filename, true);

        // intermediate file with ila, without probes
        filename = iii_dir.getAbsolutePath() + '/' + no_ila_dcp_file.getName().replaceFirst("_wrapper_[\\S|\\s]*", "_ila.dcp");
        no_probes_dcp_file = getExistingFile(filename, false);

        // input file for probes
        files = arg_map.get("input_probes_file");
        filename = (files == null) ? null : files.get(0);
        input_probes_file = getExistingFile(filename, true);


        // Output files

        // final routed design with ila and probes
        files = arg_map.get("output_dcp");
        if(files == null){
            filename = no_ila_dcp_file.getName().replaceFirst("_wrapper_[\\S|\\s]*", "_probes.dcp");
            output_dcp_file = new File(pwd_dir, filename);
        }
        else {
            filename = files.get(0);
            if(filename.startsWith("/") || filename.startsWith("~"))
                output_dcp_file = new File(filename);
            else // assume filename relative to pwd
                output_dcp_file = new File(pwd_dir, filename);
        }
        // output_dcp_file = getExistingFile(filename, false);

        // output file for probes        
        files = arg_map.get("output_probes_file");
        if(files == null){
            filename = FileTools.removeFileExtension(output_dcp_file.getName()) + "_probes.txt";
            output_probes_file = new File(output_dcp_file.getParent(), filename);
        }
        else {
            filename = files.get(0);
            if(filename.startsWith("/") || filename.startsWith("~"))
                output_dcp_file = new File(filename);
            else // assume filename relative to pwd
                output_dcp_file = new File(pwd_dir, filename);
        }
    }

    /**
     * Loads the most recent design that can be found or loads from the original design
     * without ila if refresh was input to the command line.
     * @return An integer indicating what step to continue at. (0 = original design, 1 = design with ila)
     */
    private int loadDesign(){
        boolean differs_from_metadata = false;
        differs_from_metadata = loadProbeDepth() || differs_from_metadata;
        differs_from_metadata = loadClkNet() || differs_from_metadata;

        if(no_ila_dcp_file == null){
            if(no_probes_dcp_file == null)
                MessageGenerator.briefErrorAndExit("Couldn't find any valid starting dcp file.\n");

            ArrayList<String> files = arg_map.get("input_dcp");
            if(files == null)
                printIfVerbose("Couldn't find input_dcp file 'null'.");
            else
                printIfVerbose("Couldn't find input_dcp file '" + files.get(0) + "'.");
            
            // no_ila doesn't exist but no_probes does
            
            // if user requested refresh, must use no_ila
            if(arg_map.containsKey("refresh")){
                MessageGenerator.briefErrorAndExit("Canceling operation. Refresh requested,"
                        + " but can't find input_dcp.\n");
            }
            else if(differs_from_metadata){
                MessageGenerator.briefErrorAndExit("Canceling operation. Requested args differ from metadata,"
                        + " but can't find input_dcp.\n");
            }
            design = safeReadCheckpoint(no_probes_dcp_file);
            return 1;
        }
        else {
            if(no_probes_dcp_file == null){
                printIfVerbose("No intermediate dcp file was found.");

                // no_probes doesn't exist but no_ila does
                design = safeReadCheckpoint(no_ila_dcp_file);
                return 0;
            }
            else { // both exist
                if(arg_map.containsKey("refresh") || differs_from_metadata
                        || FileTools.isFileNewer(no_ila_dcp_file.getAbsolutePath(), 
                                                    no_probes_dcp_file.getAbsolutePath())){
                    // no_ila is newer than no_probes or refresh requested
                    design = safeReadCheckpoint(no_ila_dcp_file);
                    return 0;
                }
                design = safeReadCheckpoint(no_probes_dcp_file);
                return 1;
            }
        }
    }

    /**
     * Ensure that if the user has not specified to force overwrite, output files for
     * dcp, probes and ltx don't collide with already existing files.
     */
    private void checkForFileCollisions(){
        // if not force overwrite check if any files will collide warn against and exit.
        // after this, no checks are made for overwriting files
        if(!force()){
            // intermediate can always be overwritten without force
            boolean any_err = false;
            // check output_dcp, output_ltx, output_probes
            if(output_dcp_file.exists()){
                MessageGenerator.briefError("The output dcp would overwrite another file at '"
                        + output_dcp_file.getAbsolutePath() + "'.");
                any_err = true;
            }
            if(output_probes_file.exists()){
                MessageGenerator.briefError("The output probes file would overwrite another file at '"
                        + output_probes_file.getAbsolutePath() + "'.");
                any_err = true;
            }
            String filename = FileTools.removeFileExtension(output_dcp_file.getName()) + ".ltx";
            File output_ltx_file = new File(output_dcp_file.getParent(), filename);
            if(output_ltx_file.exists()){
                MessageGenerator.briefError("The output ltx would overwrite another file at '"
                        + output_ltx_file.getAbsolutePath() + "'.");
                any_err = true;
            }
            // if any would overwrite, exit
            if(any_err)
                MessageGenerator.briefErrorAndExit("Use force (-f) to overwrite.\nCanceling operation.\n");
        }
    }

    /**
     * This function searches the top level user design for a reset (or rst) net if it can find one.
     * This net will be used to connect unused probe wires since unconnected probe wires cause errors.
     * @param input_probes A collection of probe strings to use to find the name of the top instance of the user design.
     */
    private void setDefaultNet(Collection<String> input_probes){
        List<EDIFNet> reset_nets = new ArrayList<>();
        List<EDIFNet> rst_nets = new ArrayList<>();
        List<EDIFNet> other_nets = new ArrayList<>();
        String extra_net = null;
        String dsgn_inst = null;

        // Try to find a reset net to connect to the unconnected pins
        try {
            for(String hier_name : input_probes){
                try {
                    String[] path = hier_name.split("/");
                    dsgn_inst = path[0];
                    break;
                } catch(IndexOutOfBoundsException iob){
                    continue;
                }
            }
            if(dsgn_inst == null)
                throw new NullPointerException();

            for(EDIFNet n : design.getNetlist().getTopCell().getNets()){
                String name = n.getName().toLowerCase();
                boolean buffered_name = n.getName().contains("BUF");

                if(name.contains("reset") && !buffered_name)
                    reset_nets.add(n);
                else if(name.contains("rst") && !buffered_name)
                    rst_nets.add(n);
                else if(!name.contains("clk") && !name.contains("clock") && !buffered_name) // not a clk net
                    other_nets.add(n);
            }

            for(EDIFNet n : reset_nets){
                default_net = dsgn_inst + "/" + n.getName();
                if(EDIFTools.getNet(design.getNetlist(), default_net) != null)
                    return;
            }
            for(EDIFNet n : rst_nets){
                default_net = dsgn_inst + "/" + n.getName();
                if(EDIFTools.getNet(design.getNetlist(), default_net) != null)
                    return;
            }
            printIfVerbose("\nNo nets found in top module containing 'reset' or 'rst'.");
            printIfVerbose("Selecting net to connect unused probes to at random.");
            for(EDIFNet n : other_nets){
                default_net = dsgn_inst + "/" + n.getName();
                if(EDIFTools.getNet(design.getNetlist(), default_net) != null)
                    return;
            }
            printIfVerbose("\nFailed to find a net to connect unused probes to.");
        } catch(NullPointerException npe){
            printIfVerbose("\nFailed to find a net to connect unused probes to.");
            default_net = null;
        }
    }

    /**
     * Set probe_map using data from input_probes_file.
     * This function populates probe_map and ensures that the entries in it cover probes
     * numbering 0 to probe_map.size()-1.
     */
    public void getProbesFromFile(){
        Map<String, String> input_probes = ProbeRouter.readProbeRequestFile(input_probes_file.getAbsolutePath());
        String[] probe_str = {"top/u_ila_0/probe0[", "]"};
        LinkedList<String> bad_probe = new LinkedList<>();
        TreeSet<Integer> probe_nums = new TreeSet<>();
        probe_map = new TreeMap<>(naturalComparator);

        // set default net
        setDefaultNet(input_probes.values());

        // add probe->net pairs that are valid (conform to probe_str) to probe_map and the probe index to probe_nums
        // if probe is invalid, add net to bad_probes
        int p_num = -1;
        for(Entry<String, String> p : input_probes.entrySet()){
            if(p.getKey().startsWith(probe_str[0]) && p.getKey().endsWith(probe_str[1])){
                try {
                    p_num = Integer.parseInt(p.getKey().substring(probe_str[0].length(), 
                                    p.getKey().length() - probe_str[1].length()));
                    probe_nums.add(p_num);
                    probe_map.put(p.getKey(), p.getValue());
                } catch(NumberFormatException nfe){
                    bad_probe.add(p.getValue());
                }
            }
            else
                bad_probe.add(p.getValue());
        }
        /* for(contiguous i = 0 to end)
         *   continue if:
         *      there are more nets in bad_probe (as long as i does not exceed MAX_PROBE_COUNT)
         *      or there is a good probe with a higher index than i
         */
        for(int i = 0 ; i < probe_nums.last() || (!bad_probe.isEmpty() && i < MAX_PROBE_COUNT) ; i++){
            if(probe_nums.contains(i))
                continue;
            if(!bad_probe.isEmpty()){
                String s = bad_probe.pollFirst();
                probe_map.put(probe_str[0] + i + probe_str[1], s);
            }
            else
                probe_map.put(probe_str[0] + i + probe_str[1], default_net);
        }
    }

    /**
     * Adds default connections to probe_map so that it's size is equal to p_count.
     * @param p_count Number of probes desired.
     */
    private void padProbeMap(int p_count){
        String[] probe_str = {"top/u_ila_0/probe0[", "]"};
        for(int i = probe_map.size() ; i < p_count ; i++)
            probe_map.put(probe_str[0] + i + probe_str[1], default_net);
    }

    /**
     * Load the probes map from input_probes_file if specified, else from nets marked for debug.
     * Also sets the probe count from command line if given, else sets it same as size of probe map.
     */
    private int loadProbes(int step){
        // if given probe file, load from it
        if(arg_map.containsKey("input_probes_file")){
            getProbesFromFile();

            if(probe_map == null || probe_map.size() < 1)
                MessageGenerator.briefErrorAndExit("No probes found in probe file '"
                        + input_probes_file.getAbsolutePath() + "'.\nExiting.");
            else if(probe_map.size() > MAX_PROBE_COUNT)
                MessageGenerator.briefErrorAndExit("Too many probes (or too high index probes) "
                        + "found in probe file '" + input_probes_file.getAbsolutePath() 
                        + "'.\nMaximum index of a probe is " + (MAX_PROBE_COUNT-1) + " .\nExiting.");
        }
        // load from nets marked for debug
        else {
            List<String> debug_nets = ILAInserter.getNetsMarkedForDebug(design);
            setDefaultNet(debug_nets);
            probe_map = new HashMap<>();
            
            String[] probe_str = {"top/u_ila_0/probe0[", "]"};
            for(int i = 0 ; i < debug_nets.size() && i < MAX_PROBE_COUNT; i++)
                probe_map.put(probe_str[0] + i + probe_str[1], debug_nets.get(i));

            if(probe_map.size() < 1){
                StringBuilder sb = new StringBuilder();
                sb.append("No nets marked for debug in '");
                if(step == 0)
                    sb.append(no_ila_dcp_file.getAbsolutePath());
                else if(step == 1)
                    sb.append(no_probes_dcp_file.getAbsolutePath());
                else
                    sb.append("????");
                sb.append("'.\nExiting.");
                MessageGenerator.briefErrorAndExit(sb.toString());
            }
            else if(debug_nets.size() > MAX_PROBE_COUNT)
                MessageGenerator.briefMessage("\nMore than " + MAX_PROBE_COUNT + " nets marked for debug. \n"
                        + "Truncating list of debug nets.");
        }

        ArrayList<String> list = arg_map.get("probe_count");
        if(list != null){
            try {
                int np = Integer.parseInt(list.get(0));
                if(probe_map.size() > np){
                    printIfVerbose("\nIgnoring input probe_count of '" + np + "'.");
                    printIfVerbose("It is smaller than probe_map size of '" + probe_map.size() + "'.");
                    probe_count = probe_map.size();
                }
                else if(np > MAX_PROBE_COUNT){
                    printIfVerbose("\nIgnoring input probe_count of '" + np + "'.");
                    printIfVerbose("It is larger than maximum number of probes (" + probe_map.size() + ").");
                    probe_count = probe_map.size();
                }
                else {
                    probe_count = np;
                    padProbeMap(np);
                }
            } catch (NumberFormatException nfe) {
                printIfVerbose("Couldn't parse '" + list.get(0) + "' as an integer probe count.");
                probe_count = probe_map.size();
            }
        }
        else
            probe_count = probe_map.size();
        
        printIfVerbose("\nProbe count is " + probe_count + ".");

        if(step == 0)
            return 0;

        // check if intermediate soln has enough probe wires
        int p_count = -1;
        try {
            Collection<EDIFPort> ports = design.getNetlist().getTopCell().getCellInst("top").getCellPorts();
            for(EDIFPort p : ports)
                if(p.toString().startsWith("probes"))
                    p_count = p.getWidth();
            
            if(p_count == -1)
                throw new NullPointerException();

            printIfVerbose("Width of probes bus in ila of intermediate design is " + p_count + ".");
        } catch(NullPointerException npe){
            printIfVerbose("Couldn't find width of probes bus in ila. The intermediate design being "
                    + "used might not be usable by this application.");
        }

        if(p_count < probe_count){
            printIfVerbose("Not enough wires. Must add an ila with more wires to input dcp.");

            design = safeReadCheckpoint(no_ila_dcp_file);
            return 0;
        }
        else
            padProbeMap(p_count);
        return step;
    }

    /**
     * Modified from EDIFTools.connectDebugProbe()
     * 
	 * Specialized function to connect a debug port within an EDIF netlist.  
	 * @param topPortNet The top-level net that connects to the debug core's input port.
	 * @param routedNetName The name of the routed net whose source is the net we need to connect to
	 * @param newPortName The name of the port to be added at each level of hierarchy
	 * @param parentInst The instance where topPortNet resides
	 * @param instMap The map of the design created by {@link EDIFTools#generateCellInstMap(EDIFCellInst)} 
	 */
	private static void my_connectDebugProbe(EDIFNet topPortNet, String routedNetName, String newPortName, 
			EDIFHierCellInst parentInst, EDIFNetlist n, HashMap<EDIFCell, ArrayList<EDIFCellInst>> instMap){
		EDIFNet currNet = topPortNet;
		String currParentName = parentInst.getHierarchicalInstName();
		EDIFCellInst currInst = parentInst.getInst();
		// Need to check if we need to move up levels of hierarchy before we move down
		while(!routedNetName.startsWith(currParentName)){
			EDIFPort port = currInst.getCellType().createPort(newPortName, EDIFDirection.INPUT, 1);
			currNet.createPortInst(port);
			EDIFCellInst prevInst = currInst;
			try{
				currParentName = currParentName.substring(0, currParentName.lastIndexOf(EDIFTools.EDIF_HIER_SEP));
			} catch(IndexOutOfBoundsException iob){
				currParentName = "";
			}
			currInst = n.getCellInstFromHierName(currParentName);
			currNet = currInst.getCellType().getNet(newPortName);
			if(currNet == null)
				currNet = new EDIFNet(newPortName, currInst.getCellType());
			currNet.createPortInst(newPortName, prevInst);
		}
		
		String[] parts = routedNetName.split(EDIFTools.EDIF_HIER_SEP);
		int idx = 0;
		if(!n.getTopCell().equals(currInst.getCellType())){
			while( idx < parts.length){
				if(parts[idx++].equals(currInst.getName())){
					break;
				}
			}
			if(idx == parts.length){
				throw new RuntimeException("ERROR: Couldn't find instance " +
					currInst.getName() + " from routed net name " + routedNetName);
			}
		}
		
		for(int i=idx; i <= parts.length-2; i++){
			currInst = currInst.getCellType().getCellInst(parts[i]);
			EDIFCell type = currInst.getCellType();
			if(instMap != null && instMap.get(type).size() > 1){
				// TODO Replicate cell type and create new
			}
			EDIFPort newPort = currInst.getCellType().createPort(newPortName, EDIFDirection.OUTPUT, 1);
			EDIFPortInst portInst = new EDIFPortInst(newPort, currNet, currInst);
			currNet.addPortInst(portInst);
			if(i == parts.length-2){
				EDIFNet targetNet = currInst.getCellType().getNet(parts[parts.length-1]);
				targetNet.createPortInst(newPort);
			}else{
				EDIFNet childNet = currInst.getCellType().getNet(topPortNet.getName());
				if(childNet == null)
					childNet = new EDIFNet(topPortNet.getName(), currInst.getCellType());
				childNet.createPortInst(newPort);
				currNet = childNet;
			}
		}
	}
    /**
     * Modified from ProbeRouter.updateProbeConnections()
     * 
     * Updates a design containing ILA (integrated logic analyzer) probe
     * connections that already exist in a design.
     */
    private void my_updateProbeConnections(){
		ArrayList<SitePinInst> pinsToRoute = new ArrayList<>(); 
		for(Entry<String,String> e : probe_map.entrySet()){
            String hierPinName = e.getKey();
            String cellInstName = EDIFTools.getHierarchicalRootFromPinName(hierPinName);
			EDIFCellInst i = design.getNetlist().getCellInstFromHierName(cellInstName);
			String pinName = hierPinName.substring(hierPinName.lastIndexOf(EDIFTools.EDIF_HIER_SEP)+1);
			EDIFPortInst portInst = i.getPortInst(pinName);
			EDIFNet net = portInst.getNet();
            String parentCellInstName = cellInstName.contains(EDIFTools.EDIF_HIER_SEP) ? cellInstName.substring(0,cellInstName.lastIndexOf(EDIFTools.EDIF_HIER_SEP)) : "";
            Net oldPhysNet = null;
            try{
                oldPhysNet = design.getNetlist().getPhysicalNetFromPin(parentCellInstName, portInst, design);
            } catch(IndexOutOfBoundsException iobe){
                oldPhysNet = null;
            }
			// Find the sink flop
			String hierInstName = cellInstName.contains(EDIFTools.EDIF_HIER_SEP) ? cellInstName.substring(0, cellInstName.lastIndexOf('/')) : ""; 
			EDIFHierPortInst startingPoint = new EDIFHierPortInst(hierInstName, portInst);
			ArrayList<EDIFHierPortInst> sinks = EDIFTools.findSinks(startingPoint);
			if(sinks.size() != 1) {
				System.err.println("ERROR: Currently we only support a single flip flop "
						+ "sink for probe re-routes, found " + sinks.size() + " on " + e.getKey() + ", skipping...");
				continue;
			}
				
			EDIFHierPortInst sinkFlop = sinks.get(0);
            Cell c = design.getCell(sinkFlop.getFullHierarchicalInstName());
            SitePinInst physProbeInPin = null;
            try{
                physProbeInPin = c.unrouteLogicalPinInSite(sinkFlop.getPortInst().getName());
            } catch(NullPointerException npe){
                physProbeInPin = null;
            }
			
			// Disconnect probe from current net
			net.removePortInst(portInst);
			// Unroute the portion of physical route to old probe net
			if(physProbeInPin != null) 
				oldPhysNet.removePin(physProbeInPin,true);
			
			// Connect probe to new net
			String newPortName = "rw_"+ pinName;
			EDIFNet newNet = net.getParentCell().createNet(newPortName);
			newNet.addPortInst(portInst);

			EDIFCellInst parent = design.getNetlist().getCellInstFromHierName(parentCellInstName);
			EDIFHierCellInst parentInst = new EDIFHierCellInst(parentCellInstName, parent);
			EDIFTools.connectDebugProbe(newNet, e.getValue(), newPortName, parentInst, design.getNetlist(), null);
			
			String parentNet = design.getNetlist().getParentNetName(e.getValue());
			Net destPhysNet = design.getNet(parentNet);
			
            // Route the site appropriately
            
			String sitePinName = c.getBELName().charAt(0) + "X";
			BELPin inPin = c.getBEL().getPin(c.getPhysicalPinMapping(sinkFlop.getPortInst().getName()));
			c.getSiteInst().routeIntraSiteNet(destPhysNet, c.getSite().getBELPin(sitePinName), inPin);
			
			if(physProbeInPin == null){
				// Previous connection was internal to site, need to route out to site pin
				physProbeInPin = new SitePinInst(false, sitePinName, c.getSiteInst());
			}
			destPhysNet.addPin(physProbeInPin);
			pinsToRoute.add(physProbeInPin);
		}
		
		// Attempt route new net to probe
		// TODO - Should we add a flop?
		Router r = new Router(design);
		r.routePinsReEntrant(pinsToRoute, false);
    }
    
    /**
     * Main workflow to generate a placed and routed dcp which has the original design 
     * plus an ila and probes.
     * @param args Command line arguments. Usage described in help (-h or --help).
     */
    public void start(String[] args){
        TCLScript script = null;
        String filename = null;

        mapArgs(args); // parse arguments
        setFiles(); // find files that were input
        checkForFileCollisions();
        readMetadata();
        int step = loadDesign();
        step = loadProbes(step);
        
        // add ila to design
        if(step == 0){
            // add ila
            //TODO future work: cache ila.dcp files (from .ila) generated so that they can be reused.
            if(no_probes_dcp_file == null){
                filename = no_ila_dcp_file.getName().replaceFirst("_wrapper_[\\S|\\s]*", "_ila.dcp");
                no_probes_dcp_file = new File(iii_dir, filename);
            }
            filename = no_probes_dcp_file.getAbsolutePath();

            // If got here because changing the number of probes, we will overwrite the <design>_ila.dcp in 
            // due course. We must also delete the <design>_ila.edf.
            File edif = new File(no_probes_dcp_file.getParentFile(), no_probes_dcp_file.getName().replace(".dcp", ".edf"));
            if(edif.exists()){
                printIfVerbose("Deleting intermediate edif file '" + edif.getAbsolutePath() + "'.");
                FileTools.deleteFile(edif.getAbsolutePath());
            }            

            String filename_bad_edif = filename.replace(".dcp", "_bad_edif.dcp");

            String[] ila_inserter_args = {
                no_ila_dcp_file.getAbsolutePath(),
                filename_bad_edif,
                Integer.toString(probe_count),
                Integer.toString(probe_depth),
                clk_net
            };
            writeMetadata();

            // Add ila and write intermediate checkpoint
            ILAInserter.main(ila_inserter_args);

            // Modify the edif file so vivado will open the checkpoint.
            fixEdifInDCP(filename_bad_edif, filename);
            
            // place design
            script = new TCLScript(filename, filename, "place_design.tcl");
            script.add(TCLEnum.PLACE);
            script.add(TCLEnum.WRITE_DCP, "f" + (quiet() ? "q" : ""));
            script.run();

            design = safeReadCheckpoint(no_probes_dcp_file);
        }

        // route probes into design
        printIfVerbose("\nStarting to route probes into design.");
        my_updateProbeConnections();
        printIfVerbose("Finished routing probes.\n");
        filename = output_dcp_file.getAbsolutePath();
        design.writeCheckpoint(filename);
        
        // write probes
        writeProbesFile(output_probes_file.getAbsolutePath());

        script = new TCLScript(filename, filename, "place_route_write.tcl");
        script.add(TCLEnum.ROUTE);      // route
        script.add(TCLEnum.WRITE_LTX);  // write ltx, dcp, bitstream
        script.add(TCLEnum.WRITE_DCP);
        script.add(TCLEnum.WRITE_BITSTREAM);
        script.run();

        printIfVerbose("\nFinal outputs written.");
        printIfVerbose("Finished.");
    }

    public static void main(String[] args){
        ILADebug ila_dbg = new ILADebug();
        ila_dbg.start(args);
    }
}