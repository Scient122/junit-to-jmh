import json
import sys

file_path = sys.argv[1]
output_file_path = sys.argv[2]
print(output_file_path)

try:
    
    with open(file_path, 'r') as file:
        
        data = json.load(file)
        
        for benchmark in data:
            benchmark_name = benchmark['benchmark']
            fork_count = 1
            for fork in benchmark['primaryMetric']['rawDataHistogram']:
                iteration_averages = []
                iteration_count =1
                for iteration in fork:
                    sum = 0
                    count = 0
                    for value in iteration:
                        sum = sum + (value[0] * value[1])
                        count = count + value[1]

                    average = sum / count 
                    iteration_averages.append({'iteration' : iteration_count,'average' : average})

                    iteration_count = iteration_count +1

                output_file_name = output_file_path+''+benchmark_name+'Fork'+str(fork_count)+'.json'
                with open(output_file_name, 'w') as json_file:
                    #output = json.load(json_file)
                    #output['results'] = iteration_averages
                    json.dump(iteration_averages, json_file, indent=4)

                fork_count = fork_count+1
                

            

        
        

       
       

except FileNotFoundError:
    print(f'Errore: Il file {file_path} non è stato trovato.')
except json.JSONDecodeError:
    print('Errore: Il file non è un JSON valido o è danneggiato.')
except KeyError:
    print('Errore: Il campo "mode" non esiste nel file JSON specificato.')