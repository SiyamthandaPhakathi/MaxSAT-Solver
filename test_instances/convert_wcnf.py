import sys
import os
import re

def convert_file(input_path, output_path):
    nvars = 0
    ncls = 0
    max_soft_wt = 0
    found_nvars = False

    print(f"Scanning: {input_path}")
    
    # Pass 1: Gather statistics and find the maximum soft weight
    with open(input_path, 'r') as f:
        for line in f:
            stripped = line.strip()
            if not stripped:
                continue
            
            # Look for the new JSON-like header variables
            if stripped.startswith('c'):
                if '"nvars":' in stripped:
                    match = re.search(r'"nvars":\s*(\d+)', stripped)
                    if match:
                        nvars = int(match.group(1))
                        found_nvars = True
                continue

            # Skip traditional problem lines if the file already has them
            if stripped.startswith('p'):
                continue

            # It is a valid clause line
            ncls += 1

            if not stripped.startswith('h'):
                # It is a soft clause. The first token is the weight.
                wt_str = stripped.split(maxsplit=1)[0]
                wt = int(wt_str)
                if wt > max_soft_wt:
                    max_soft_wt = wt

            # Fallback: if the header is missing, find the highest variable ID manually
            if not found_nvars:
                tokens = stripped.split()
                # Start looking at index 1 to skip the 'h' or the soft weight
                for t in tokens[1:]:
                    val = abs(int(t))
                    if val > nvars:
                        nvars = val

    topweight = max_soft_wt + 1000000
    print(f"  -> Extracted: {nvars} vars, {ncls} clauses, max soft weight: {max_soft_wt}")
    print(f"  -> Calculated topweight: {topweight}")

    # Pass 2: Write the converted file
    with open(input_path, 'r') as fin, open(output_path, 'w') as fout:
        # Write the traditional problem line
        fout.write(f"p wcnf {nvars} {ncls} {topweight}\n")

        for line in fin:
            stripped = line.strip()
            
            # Skip empty lines to prevent gaps
            if not stripped:
                continue
            
            # Keep original comments
            if stripped.startswith('c'):
                fout.write(f"{stripped}\n")
                continue
                
            # Skip old problem lines since we already wrote a new one
            if stripped.startswith('p'):
                continue

            # Replace 'h' with the calculated top weight
            if stripped.startswith('h'):
                # stripped[1:] safely removes the 'h' without keeping the old newline
                fout.write(f"{topweight} {stripped[1:].lstrip()}\n")
            else:
                # Soft clauses are written exactly as is, with a single strict newline
                fout.write(f"{stripped}\n")

    print(f"  -> Saved converted file to: {output_path}\n")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python convert_wcnf.py <input.wcnf> [output.wcnf]")
        print("   Or: python convert_wcnf.py <directory_of_wcnf_files>")
        sys.exit(1)

    input_arg = sys.argv[1]

    # Handle a single directory ONLY (no subfolders)
    if os.path.isdir(input_arg):
        for filename in os.listdir(input_arg):
            # Only process .wcnf files and ignore already converted ones
            if filename.endswith(".wcnf") and not filename.startswith("converted_"):
                input_path = os.path.join(input_arg, filename)
                # Ensure it is a file and not a directory
                if os.path.isfile(input_path):
                    output_path = os.path.join(input_arg, f"converted_{filename}")
                    convert_file(input_path, output_path)
                
    # Handle a single file
    elif os.path.isfile(input_arg):
        output_path = sys.argv[2] if len(sys.argv) == 3 else f"converted_{os.path.basename(input_arg)}"
        convert_file(input_arg, output_path)
        
    else:
        print(f"Error: {input_arg} is not a valid file or directory.")