# Copyright 2014 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This is a quick and dirty rule to make Bazel compile itself.  It
# only supports Java.

proto_filetype = FileType([".proto"])

def gensrcjar_impl(ctx):
  out = ctx.outputs.srcjar
  proto_output = out.path + ".proto_output"
  proto_compiler = ctx.file._proto_compiler

  proto_compiler_command_parts = [proto_compiler.path, "--java_out=" + proto_output, ctx.file.src.path]
  if ctx.attr.gen_grpc:
    proto_compiler_command_parts += [
      "--plugin=protoc-gen-java_rpc=" + ctx.file._gen_java_grpc.path,
      "--java_rpc_out=" + proto_output,
    ] 

  sub_commands = [
    "rm -rf " + proto_output,
    "mkdir " + proto_output,
    ' '.join(proto_compiler_command_parts),
    "touch -t 198001010000 $(find " + proto_output + ")",
    ctx.file._jar.path + " cMf " + out.path + " -C " + proto_output + " .",
  ]

  inp = ctx.files.deps + [ctx.file.src, proto_compiler, ctx.file._jar] + ctx.files._jdk + [ctx.file._gen_java_grpc]

  ctx.action(
    command=" && ".join(sub_commands),
    inputs=inp,
    outputs=[out],
    mnemonic="GenProtoSrcJar",
    use_default_shell_env = True)

gensrcjar = rule(
  gensrcjar_impl,
  attrs={
      "src": attr.label(allow_files=proto_filetype, single_file=True),
      "deps": attr.label_list(allow_files=proto_filetype),
      "gen_grpc": attr.bool(),
      # TODO(bazel-team): this should be a hidden attribute with a default
      # value, but Skylark needs to support select first.
      "_proto_compiler": attr.label(
          default=Label("@bazel_tools//third_party:protoc"),
          allow_files=True,
          single_file=True),
      "_gen_java_grpc": attr.label(
          default=Label("//third_party/grpc-java-compiler:gen_java_grpc"),
          allow_files=True,
          single_file=True),
      "_jar": attr.label(
          default=Label("@bazel_tools//tools/jdk:jar"),
          allow_files=True,
          single_file=True),
      "_jdk": attr.label(
          default=Label("@bazel_tools//tools/jdk:jdk"),
          allow_files=True),
  },
  outputs={"srcjar": "lib%{name}.srcjar"},
)

# TODO(bazel-team): support proto => proto dependencies too
def proto_java_library(name, src, gensrcjar_deps = [], deps = [], gen_grpc = False):
  gensrcjar(name=name + "_srcjar", src=src, deps=gensrcjar_deps, gen_grpc = gen_grpc)

  java_deps = ["@bazel_tools//third_party:protobuf"] + deps
  if gen_grpc:
    java_deps += ["//third_party/grpc", "//third_party/guava"]

  native.java_library(
    name=name,
    srcs=[name + "_srcjar"],
    deps=java_deps,
    # The generated code has lots of 'rawtypes' warnings.
    javacopts=["-Xlint:-rawtypes"],
)
