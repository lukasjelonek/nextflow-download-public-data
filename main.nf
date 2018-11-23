params.sra_accessions = "SRR1965912,SRR1965913"
/* Database.from(db: 'sra',format: "fastq", accessions: params.sra_accessions ).view().set{ch_data} */
Database.from(db: 'nucleotide',format: "embl", accessions: 'A00145,A00146' ).view().set{ch_data}


process test {
input:
file f from  ch_data

script:
"""
echo $f
"""
}
