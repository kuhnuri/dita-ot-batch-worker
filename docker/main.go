package main

import (
	"bufio"
	"fmt"
	kuhnuri "github.com/kuhnuri/go-worker"
	"io/ioutil"
	"log"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

type Args struct {
	src  *url.URL
	dst  *url.URL
	tmp  string
	out  string
	args []string
}

func readArgs() *Args {
	input := os.Getenv("input")
	if input == "" {
		log.Fatalf("Input environment variable not set")
	}
	output := os.Getenv("output")
	if output == "" {
		log.Fatalf("Output environment variable not set")
	}
	src, err := url.Parse(input)
	if err != nil {
		log.Fatalf("Failed to parse input argument %s: %v", input, err)
	}
	dst, err := url.Parse(output)
	if err != nil {
		log.Fatalf("Failed to parse output argument %s: %v", output, err)
	}
	tmp, err := ioutil.TempDir("", "tmp")
	if err != nil {
		log.Fatalf("Failed to create temporary directory: %v", err)
	}
	out, err := ioutil.TempDir("", "out")
	if err != nil {
		log.Fatalf("Failed to create temporary directory: %v", err)
	}
	return &Args{src, dst, tmp, out, os.Args[1:]}
}

func getClasspath(base string) string {
	var jars []string
	jars = append(jars, filepath.Join(base, "config"))
	filepath.Walk(base, func(src string, info os.FileInfo, err error) error {
		if filepath.Ext(src) == ".jar" {
			jars = append(jars, src)
		}
		return nil
	})
	env, err := os.Open(filepath.Join(base, "config/env.sh"))
	if err != nil {
		log.Fatalf("Failed to open env.sh: %v", err)
	}
	scn := bufio.NewScanner(env)
	if err != nil {
		log.Fatalf("Failed to read env.sh: %v", err)
	}
	for scn.Scan() {
		line := scn.Text()
		if strings.HasPrefix(line, "CLASSPATH") {
			src := filepath.Join(base, line[33:len(line)-1])
			jars = append(jars, src)
		}
	}
	if err := scn.Err(); err != nil {
		log.Fatal(err)
	}

	return strings.Join(jars, string(os.PathListSeparator))
}

func convert(src string, dstDir string, args []string) error {
	base := "/opt/app"

	cmd := exec.Command("java",
		"-cp", getClasspath(base),
		"-Dant.home="+base,
		"org.apache.tools.ant.Main",
		"-Dargs.input", src,
		"-Doutput.dir", dstDir)
	cmd.Args = append(cmd.Args, args...)
	fmt.Printf("Args: %s", cmd.Args)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stdout

	if err := cmd.Run(); err != nil {
		return fmt.Errorf("ERROR: Failed to convert: %v", err)
	}

	return nil
}

func main() {
	args := readArgs()

	start, err := kuhnuri.DownloadFile(args.src, args.tmp)
	if err != nil {
		log.Fatalf("Failed to download %s: %v", args.src, err)
	}

	if err := convert(start, args.out, args.args); err != nil {
		log.Fatalf("Failed to convert %s: %v", args.tmp, err)
	}

	if err := kuhnuri.UploadFile(args.out, args.dst); err != nil {
		log.Fatalf("Failed to upload %s: %v", args.dst, err)
	}
}
