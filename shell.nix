{ pkgs ? import <nixpkgs> {}, ... }:

pkgs.mkShell {
  packages = with pkgs; [jdk25 python314 pipenv nodejs_24 typst pandoc libreoffice-still libwebp qpdf];

  # The backend resolves each external binary through its own *_BIN variable and falls back to
  # a bare name on PATH. Pointing the variables at the store paths makes the tools reachable for
  # anything that does not inherit this shell's PATH, and pins the exact build the shell provides.
  CWEBP_BIN = "${pkgs.libwebp}/bin/cwebp";
  TYPST_BIN = "${pkgs.typst}/bin/typst";
  PANDOC_BIN = "${pkgs.pandoc}/bin/pandoc";
  LIBREOFFICE_BIN = "${pkgs.libreoffice-still}/bin/libreoffice";
  QPDF_BIN = "${pkgs.qpdf}/bin/qpdf";
}
