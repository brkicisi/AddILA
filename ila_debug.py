# Igi Brkic
# 16 May 2019
# python v3.5.3

import os
import argparse
import sys
import subprocess


def main():
	"""This script assists in debugging of Vivado projects by allowing easy addition of an ILA to a pre-routed design using RapidWright.
	
	Functionality:
		Add an ILA to a design.
		Add probes to a design with an ILA inserted by this script. Probes can be input from a text file map (lines are 'probe_net design_net') or from nets marked for debug.
	
	----
	['-h, '--help']		Show help message.
	<input_dcp>			Input design checkpoint (.dcp) file.
	<output_dcp>		Output dcp file.
	['-i, '--probes_file' <probes_txt>]	File specifying what probes and nets are to be connected together
	['-r, '--refresh']	Force recompilation from scratch.
	['-f, '--force']	Force file overwrite.
	['-q, '--quiet']	Execute quietly.
	"""
	
	parser = argparse.ArgumentParser(description="This script assists in debugging of Vivado projects by allowing easy addition of an ILA to a pre-routed design using RapidWright.")
	parser.add_argument('input_dcp', nargs=1, help="Input design checkpoint (.dcp) file.")
	parser.add_argument('output_dcp', nargs=1, help="Output dcp file.")
	parser.add_argument('-i', '--probes_file', nargs=1, dest='probes_file', default=None, help="File specifying what probes and nets are to be connected together.")
	parser.add_argument('-r', '--refresh', dest='refresh', action='store_true', help="Force recompilation from scratch.")
	parser.add_argument('-q', '--quiet', dest='quiet', action='store_true', help="Suppress messages.")
	parser.add_argument('-f', '--force', dest='force', action='store_true', help="Overwrite file at destination if it exists.")
	args = parser.parse_args()
	
	try:
		pwd = subprocess.check_output("pwd").decode(sys.stdout.encoding).strip()
	except:
		print("\nERROR: Failed to execute 'pwd'.")
		return 1
	
	if args.input_dcp[0][0] in {'~', '/'}:
		input_dcp = args.input_dcp[0]
	else:
		input_dcp = pwd + "/" + args.input_dcp[0]
	if args.output_dcp[0][0] in {'~', '/'}:
		output_dcp = args.output_dcp[0]
	else:
		output_dcp = pwd + "/" + args.output_dcp[0]
	
	if args.probes_file == None:
		probes_file = None
	else:
		if args.probes_file[0][0] in {'~', '/'}:
			probes_file = " " + args.probes_file
		else:
			probes_file = " " + pwd + "/" + args.probes_file
	
	
	
	


if __name__ == "__main__":
    main()
