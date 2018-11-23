@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7')
import groovyx.net.http.RESTClient
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.*

import nextflow.Channel
import static nextflow.Nextflow.file

class Database {

    static Map providers = [
        'sra': ['fastq': new EnaSraFastqProvider()],
        'nucleotide': ['fasta': new EnaNucleotideProvider(),
                       'embl': new EnaNucleotideProvider() ]
    ]

    static def from(Map parameters) {
        println("Calling database from with parameters ${parameters}")
        if (providers.containsKey(parameters['db'])){
            if (providers[parameters['db']].containsKey(parameters['format'])){
                def provider = providers[parameters['db']][parameters['format']]
                if (!(parameters.accessions instanceof List)) {
                    parameters.accessions = parameters.accessions.split(',')
                }
                println (parameters)
                // TODO include reading accession files before passing them over to the handler
                provider.handle(parameters)
            } else {
                throw new RuntimeException("Format ${parameters.format} is not supported for database ${parameters.db} is not supported (yet?).")
            }
        } else {
            throw new RuntimeException("Database ${parameters.db} is not supported (yet?).")
        }
    }

}

class EnaSraFastqProvider {

    def handle(Map parameters) {
        return fromSraAccessions(parameters.accessions.join(','))
    }


    /**
     * Creates a channel with resolved fastq urls for a comma separated list of SRA accessions.
     */
    def fromSraAccessions(String accessions) {

      def client = new RESTClient('https://www.ebi.ac.uk/ena/')

      def description_locations = []

      client.get(
        path: "data/view/${accessions}&display=xml", 
        contentType: XML) { response, xml ->
          description_locations = xml.RUN.RUN_LINKS.RUN_LINK.XREF_LINK
            .findAll{it.DB.text().equals("ENA-FASTQ-FILES")}
            .ID
            .collect{it.text()}
      }

      def ftp_paths = description_locations.collect{loc -> 
        def http = new HTTPBuilder( loc )
        def result
        http.request(GET, TEXT) { req ->
          response.success = { resp, reader ->
            result = reader.text.readLines()[1] // skip header
                           .split()[1]          // take fastq field
                           .split(';')          // split it for file pairs
          }
        }
        return result
      }

      ftp_paths = ftp_paths
        .collect{ 
          it.collect{path -> file("ftp://$path")}
        }
      return Channel.from(ftp_paths)
    }
}

class EnaNucleotideProvider {

    def handle(parameters) {
        if(parameters.format == "fasta") {
            return Channel
            .fromPath("https://www.ebi.ac.uk/ena/data/view/${parameters.accessions.join(',')}&display=fasta")
            .splitFasta(by:1)
        } else if (parameters.format == "embl"){
            return Channel.from (parameters.accessions.collect{
                file("https://www.ebi.ac.uk/ena/data/view/${it}&display=text")
            })
        } else {
            throw new RuntimeException("Format ${parameters.format} not supported for EnaNucleotide")
        }

    }

}
